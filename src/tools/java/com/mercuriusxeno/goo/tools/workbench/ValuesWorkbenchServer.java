package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * The values workbench's loopback server: the single-page app, base_values.json read and saved in place,
 * the inventory, and each item's image (decision workbench-authors-base-values-whole).
 */
public final class ValuesWorkbenchServer {
    private static final String PAGE = "/workbench/index.html";
    private static final String HTML = "text/html; charset=utf-8";
    private static final String JSON = "application/json; charset=utf-8";
    private static final String PNG = "image/png";
    private static final String TEXT = "text/plain; charset=utf-8";
    private static final String GET = "GET";
    private static final String PUT = "PUT";
    private static final String POST = "POST";
    private static final String ID_PARAMETER = "id=";
    // A namespaced item id: no segment may climb out of the image folder.
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+(/[a-z0-9_.-]+)*");
    private static final String PARENT_SEGMENT = "..";
    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int NOT_ALLOWED = 405;
    private static final int SERVER_ERROR = 500;

    private final HttpServer http;
    private final WorkbenchPaths paths;
    private final InventoryDump dump;

    private ValuesWorkbenchServer(HttpServer http, WorkbenchPaths paths, InventoryDump dump) {
        this.http = http;
        this.paths = paths;
        this.dump = dump;
    }

    /**
     * Binds the loopback address and starts serving.
     *
     * @param paths the files the workbench reads and writes
     * @param dump  writes the inventory when none stands
     * @param port  the port, or 0 for any free one
     * @return the running server
     * @throws IOException where the port cannot be bound
     */
    public static ValuesWorkbenchServer start(WorkbenchPaths paths, InventoryDump dump, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        ValuesWorkbenchServer server = new ValuesWorkbenchServer(http, paths, dump);
        http.createContext("/", exchange -> server.answer(exchange, server::page));
        http.createContext("/api/model", exchange -> server.answer(exchange, server::model));
        http.createContext("/api/inventory", exchange -> server.answer(exchange, server::inventory));
        http.createContext("/api/image", exchange -> server.answer(exchange, server::image));
        http.createContext("/api/constants/evaluate", exchange -> server.answer(exchange, server::evaluateConstants));
        http.createContext("/api/constants/rename", exchange -> server.answer(exchange, server::renameConstant));
        http.createContext("/api/groups/describe", exchange -> server.answer(exchange, server::describeGroups));
        http.createContext("/api/groups/apply", exchange -> server.answer(exchange, server::applyToGroup));
        http.createContext("/api/template", exchange -> server.answer(exchange, server::template));
        http.setExecutor(Executors.newCachedThreadPool());
        http.start();
        return server;
    }

    /**
     * @return the address the page opens at
     */
    public String url() {
        return "http://" + http.getAddress().getHostString() + ":" + http.getAddress().getPort();
    }

    /**
     * Stops serving.
     */
    public void stop() {
        http.stop(0);
    }

    private interface Handler {
        Reply handle(HttpExchange exchange) throws IOException, InterruptedException;
    }

    private record Reply(int status, String contentType, byte[] body) {
        static Reply of(int status, String contentType, String body) {
            return new Reply(status, contentType, body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void answer(HttpExchange exchange, Handler handler) throws IOException {
        Reply reply;
        try {
            reply = handler.handle(exchange);
        } catch (IllegalArgumentException e) {
            reply = Reply.of(BAD_REQUEST, TEXT, e.getMessage());
        } catch (IOException e) {
            reply = Reply.of(SERVER_ERROR, TEXT, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply = Reply.of(SERVER_ERROR, TEXT, e.getMessage());
        }
        exchange.getResponseHeaders().set("Content-Type", reply.contentType());
        exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? -1 : reply.body().length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(reply.body());
        }
    }

    private Reply page(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            return Reply.of(NOT_FOUND, TEXT, "No page at " + path);
        }
        try (InputStream page = ValuesWorkbenchServer.class.getResourceAsStream(PAGE)) {
            if (page == null) {
                return Reply.of(SERVER_ERROR, TEXT, "The tools resources hold no " + PAGE);
            }
            return new Reply(OK, HTML, page.readAllBytes());
        }
    }

    private Reply model(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (GET.equals(method)) {
            BaseValuesModel model = BaseValuesModel.parse(Files.readString(paths.baseValues()));
            return Reply.of(OK, JSON, ModelJson.toJson(model, paths.baseValues().toString()));
        }
        if (PUT.equals(method)) {
            String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            BaseValuesModel current = BaseValuesModel.parse(Files.readString(paths.baseValues()));
            BaseValuesModel saved = current.withMembers(ModelJson.membersFrom(sent));
            Files.writeString(paths.baseValues(), saved.serialize());
            return Reply.of(OK, JSON, ModelJson.toJson(saved, paths.baseValues().toString()));
        }
        return Reply.of(NOT_ALLOWED, TEXT, method + " is not served here");
    }

    /**
     * Resolves the posted model's constants through the game's parser, each with its error and its readers.
     */
    private Reply evaluateConstants(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        BaseValuesModel model = postedModel(exchange);
        return Reply.of(OK, JSON, ConstantsJson.evaluated(model));
    }

    /**
     * Renames a constant in the posted model and answers the model with every reference rewritten.
     */
    private Reply renameConstant(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        ConstantsJson.Rename rename = ConstantsJson.renameFrom(sent);
        BaseValuesModel current = BaseValuesModel.parse(Files.readString(paths.baseValues()));
        BaseValuesModel renamed = current.withMembers(ModelJson.membersFrom(sent))
                .renameConstant(rename.from(), rename.to());
        return Reply.of(OK, JSON, ModelJson.toJson(renamed, paths.baseValues().toString()));
    }

    /**
     * Describes every group of the posted model against the inventory.
     */
    private Reply describeGroups(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        JsonObject answer = new JsonObject();
        answer.add("groups", GroupsJson.described(postedModel(exchange), GroupsJson.Inventory.read(
                paths.inventoryFile())));
        return Reply.of(OK, JSON, answer.toString());
    }

    /**
     * Applies one change to a group of the posted model and answers the model with every group described.
     */
    private Reply applyToGroup(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Reply.of(OK, JSON, GroupsJson.applied(sent, fileLayout(), GroupsJson.Inventory.read(
                paths.inventoryFile())));
    }

    /**
     * Answers the template the inventory builds and what the posted model leaves out of it
     * (decision template-first-then-defaults-by-convention).
     */
    private Reply template(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        GroupsJson.Inventory inventory = GroupsJson.Inventory.read(paths.inventoryFile());
        if (inventory.items().isEmpty()) {
            return Reply.of(SERVER_ERROR, TEXT, "No inventory stands at " + paths.inventoryFile());
        }
        BaseValuesModel model = postedModel(exchange);
        ValuesTemplate.Leftovers leftovers = ValuesTemplate.leftovers(model, inventory.loadout());
        JsonObject answer = new JsonObject();
        answer.add("template", com.google.gson.JsonParser.parseString(
                ModelJson.toJson(ValuesTemplate.build(inventory.loadout()), paths.baseValues().toString())));
        com.google.gson.JsonArray tags = new com.google.gson.JsonArray();
        leftovers.tags().forEach(tags::add);
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        leftovers.items().forEach(items::add);
        answer.add("ungroupedTags", tags);
        answer.add("uncoveredItems", items);
        return Reply.of(OK, JSON, answer.toString());
    }

    private BaseValuesModel postedModel(HttpExchange exchange) throws IOException {
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return fileLayout().withMembers(ModelJson.membersFrom(sent));
    }

    private BaseValuesModel fileLayout() throws IOException {
        return Files.isRegularFile(paths.baseValues())
                ? BaseValuesModel.parse(Files.readString(paths.baseValues()))
                : BaseValuesModel.parse("{\n}\n");
    }

    private Reply inventory(HttpExchange exchange) throws IOException, InterruptedException {
        dumpInventoryIfAbsent();
        if (!Files.isRegularFile(paths.inventoryFile())) {
            return Reply.of(SERVER_ERROR, TEXT, "The inventory dump wrote no " + paths.inventoryFile());
        }
        return new Reply(OK, JSON, Files.readAllBytes(paths.inventoryFile()));
    }

    /**
     * Runs the dump once, however many requests ask while it runs.
     */
    private synchronized void dumpInventoryIfAbsent() throws IOException, InterruptedException {
        if (!Files.isRegularFile(paths.inventoryFile())) {
            dump.run();
        }
    }

    private Reply image(HttpExchange exchange) throws IOException {
        String id = idParameter(exchange).orElseThrow(() -> new IllegalArgumentException("Name an item: ?id="));
        if (!ITEM_ID.matcher(id).matches() || id.contains(PARENT_SEGMENT)) {
            throw new IllegalArgumentException("Not an item id: " + id);
        }
        int colon = id.indexOf(':');
        Path image = paths.imageFolder().resolve(id.substring(0, colon)).resolve(id.substring(colon + 1) + ".png");
        if (!Files.isRegularFile(image)) {
            return Reply.of(NOT_FOUND, TEXT, "No image for " + id);
        }
        return new Reply(OK, PNG, Files.readAllBytes(image));
    }

    private static Optional<String> idParameter(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return Optional.empty();
        }
        for (String pair : query.split("&")) {
            if (pair.startsWith(ID_PARAMETER)) {
                return Optional.of(URLDecoder.decode(pair.substring(ID_PARAMETER.length()), StandardCharsets.UTF_8));
            }
        }
        return Optional.empty();
    }
}
