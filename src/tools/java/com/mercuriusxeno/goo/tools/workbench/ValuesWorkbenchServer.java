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
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final String NDJSON = "application/x-ndjson; charset=utf-8";
    private static final String PROGRESS = "progress";
    private static final String RESULT = "result";
    private static final String SECTIONS = "sections";
    private static final String EXIT_CODE = "exitCode";
    private static final String DURATION = "durationSeconds";
    private static final double NANOS_PER_SECOND = 1e9;
    private static final String ID_PARAMETER = "id=";
    // A namespaced item id: no segment may climb out of the image folder.
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+(/[a-z0-9_.-]+)*");
    private static final String PARENT_SEGMENT = "..";
    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int NOT_ALLOWED = 405;
    private static final int SERVER_ERROR = 500;
    private static final int STOP_GRACE_SECONDS = 2;

    private final HttpServer http;
    private final WorkbenchPaths paths;
    private final InventoryDump dump;
    private final DumpRunner dumps;
    // Held so Stop can shut it down: its threads are not daemons, and one left alive keeps the JVM serving.
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean stopping = new AtomicBoolean();

    private ValuesWorkbenchServer(HttpServer http, WorkbenchPaths paths, InventoryDump dump, DumpRunner dumps) {
        this.http = http;
        this.paths = paths;
        this.dump = dump;
        this.dumps = dumps;
    }

    /**
     * Binds the loopback address and starts serving.
     *
     * @param paths the files the workbench reads and writes
     * @param dump  writes the inventory when none stands
     * @param dumps runs the audit and clique dumps
     * @param port  the port, or 0 for any free one
     * @return the running server
     * @throws IOException where the port cannot be bound
     */
    public static ValuesWorkbenchServer start(WorkbenchPaths paths, InventoryDump dump, DumpRunner dumps, int port)
            throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        ValuesWorkbenchServer server = new ValuesWorkbenchServer(http, paths, dump, dumps);
        http.createContext("/", exchange -> server.answer(exchange, server::page));
        http.createContext("/api/model", exchange -> server.answer(exchange, server::model));
        http.createContext("/api/inventory", exchange -> server.answer(exchange, server::inventory));
        http.createContext("/api/image", exchange -> server.answer(exchange, server::image));
        http.createContext("/api/constants/evaluate", exchange -> server.answer(exchange, server::evaluateConstants));
        http.createContext("/api/constants/rename", exchange -> server.answer(exchange, server::renameConstant));
        http.createContext("/api/constants/delete", exchange -> server.answer(exchange, server::deleteConstant));
        http.createContext("/api/groups/describe", exchange -> server.answer(exchange, server::describeGroups));
        http.createContext("/api/groups/apply", exchange -> server.answer(exchange, server::applyToGroup));
        http.createContext("/api/dumps", exchange -> server.answer(exchange, server::standingDumps));
        http.createContext("/api/dumps/run", server::runDumps);
        http.createContext("/api/audit", exchange -> server.answer(exchange, server::auditSections));
        http.createContext("/api/values/resolve", exchange -> server.answer(exchange,
                posted -> Reply.of(OK, JSON, Files.isRegularFile(paths.inventoryFile())
                        ? Valuations.resolved(server.postedModel(posted), ValuesJson.inventory(paths.inventoryFile()))
                        : Valuations.resolved(server.postedModel(posted)))));
        http.createContext("/api/values/uncovered", exchange -> server.answer(exchange,
                posted -> Reply.of(OK, JSON, ValuesJson.uncovered(server.postedModel(posted),
                        ValuesJson.inventory(server.dumpedInventory())))));
        http.createContext("/api/values/family", exchange -> server.answer(exchange, server::family));
        http.createContext("/api/hover", exchange -> server.answer(exchange, server::hover));
        http.createContext("/api/processes", exchange -> server.answer(exchange,
                posted -> Reply.of(OK, JSON, ProcessesJson.described(server.postedModel(posted)))));
        http.createContext("/api/stop", exchange -> server.answer(exchange, server::stopRequested));
        http.setExecutor(server.executor);
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
     * Stops serving, ending the dump run in flight first; a second call does nothing.
     */
    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        dumps.stop();
        // The run's handler finishes its stream once the runner returns, so the stop waits on it briefly.
        http.stop(STOP_GRACE_SECONDS);
        executor.shutdownNow();
        stopped.countDown();
    }

    /**
     * Waits until the server stops, as the workbench's main does so the JVM exits once Stop is pressed.
     *
     * @throws InterruptedException where the wait is interrupted
     */
    public void awaitStop() throws InterruptedException {
        stopped.await();
    }

    /**
     * @param timeout how long to wait
     * @return whether the server stopped within it
     * @throws InterruptedException where the wait is interrupted
     */
    public boolean awaitStop(Duration timeout) throws InterruptedException {
        return stopped.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Answers OK, then stops on a thread of its own, since a handler cannot wait out the server it runs on
     * (decision topbar-stop-ends-every-workbench-process).
     */
    private Reply stopRequested(HttpExchange exchange) {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        dumps.stop();
        Thread stopper = new Thread(this::stop, "values-workbench-stop");
        stopper.start();
        return Reply.of(OK, TEXT, "stopping");
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
        } catch (RuntimeException | LinkageError e) {
            // Every request answers: an unanswered one leaves the page waiting with no word of the cause.
            reply = Reply.of(SERVER_ERROR, TEXT, e.getClass().getSimpleName() + ": " + e.getMessage());
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
     * Deletes a constant from the posted model with its cascade of readers and answers the model left
     * (decision deleting-a-variable-inlines-its-expression-into-each-reader).
     */
    private Reply deleteConstant(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        BaseValuesModel current = BaseValuesModel.parse(Files.readString(paths.baseValues()));
        BaseValuesModel deleted = current.withMembers(ModelJson.membersFrom(sent))
                .deleteConstant(ConstantsJson.deleteFrom(sent));
        return Reply.of(OK, JSON, ModelJson.toJson(deleted, paths.baseValues().toString()));
    }

    /**
     * Describes every group of the posted model against the inventory.
     */
    private Reply describeGroups(HttpExchange exchange) throws IOException, InterruptedException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        JsonObject answer = new JsonObject();
        answer.add("groups", GroupsJson.described(postedModel(exchange), GroupsJson.Inventory.read(
                dumpedInventory())));
        return Reply.of(OK, JSON, answer.toString());
    }

    /**
     * Applies one change to a group of the posted model and answers the model with every group described.
     */
    private Reply applyToGroup(HttpExchange exchange) throws IOException, InterruptedException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + " is not served here");
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Reply.of(OK, JSON, GroupsJson.applied(sent, fileLayout(), GroupsJson.Inventory.read(
                dumpedInventory())));
    }

    /**
     * Answers the sections goo_audit.json carries, each rule's section, lines and flags, read afresh on every
     * request so a rule added later shows with no restart (decision each-check-is-one-rule-added-alone).
     */
    private Reply auditSections(HttpExchange exchange) throws IOException {
        if (!Files.isRegularFile(paths.auditJson())) {
            JsonObject none = new JsonObject();
            none.add(SECTIONS, new com.google.gson.JsonArray());
            return Reply.of(OK, JSON, none.toString());
        }
        return new Reply(OK, JSON, Files.readAllBytes(paths.auditJson()));
    }

    /**
     * Answers the dumps the last run left, section by section, with no run.
     */
    private Reply standingDumps(HttpExchange exchange) throws IOException {
        JsonObject answer = new JsonObject();
        answer.add(SECTIONS, DumpSections.read(paths.auditDump(), paths.cliqueDump()));
        return Reply.of(OK, JSON, answer.toString());
    }

    /**
     * Saves the posted model, runs the dumps and streams one JSON line per line the run prints, then a last
     * line holding the run's exit code, its duration and both dumps section by section
     * (decision workbench-reruns-the-audit-headless). One run at a time: the dumps write one pair of files.
     */
    private synchronized void runDumps(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            answer(exchange, ignored -> Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod()
                    + " is not served here"));
            return;
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        BaseValuesModel saved = fileLayout().withMembers(ModelJson.membersFrom(sent));
        Files.writeString(paths.baseValues(), saved.serialize());
        exchange.getResponseHeaders().set("Content-Type", NDJSON);
        exchange.sendResponseHeaders(OK, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            long start = System.nanoTime();
            int exit;
            try {
                exit = dumps.run(line -> streamLine(out, PROGRESS, new com.google.gson.JsonPrimitive(line)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exit = -1;
            } catch (IOException e) {
                streamLine(out, PROGRESS, new com.google.gson.JsonPrimitive(e.getMessage()));
                exit = -1;
            }
            JsonObject result = new JsonObject();
            result.addProperty(EXIT_CODE, exit);
            result.addProperty(DURATION, (System.nanoTime() - start) / NANOS_PER_SECOND);
            result.add(SECTIONS, DumpSections.read(paths.auditDump(), paths.cliqueDump()));
            streamLine(out, RESULT, result);
        }
    }

    private static void streamLine(OutputStream out, String field, com.google.gson.JsonElement value) {
        JsonObject line = new JsonObject();
        line.add(field, value);
        try {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            // The page closed the stream; the run carries on and its dumps still land.
        }
    }

    /**
     * Answers the Values table for the posted item's family, valued from the posted model.
     */
    private Reply family(HttpExchange exchange) throws IOException, InterruptedException {
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        BaseValuesModel model = fileLayout().withMembers(ModelJson.membersFrom(sent));
        return Reply.of(OK, JSON, Valuations.family(model, ValuesJson.inventory(dumpedInventory()),
                ValuesJson.idFrom(sent)));
    }

    /**
     * Answers the posted item's hover card, valued from the posted model.
     */
    private Reply hover(HttpExchange exchange) throws IOException, InterruptedException {
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        BaseValuesModel model = fileLayout().withMembers(ModelJson.membersFrom(sent));
        return Reply.of(OK, JSON, ValuesJson.hover(model, ValuesJson.inventory(dumpedInventory()),
                ValuesJson.idFrom(sent)));
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

    /**
     * A fresh tree holds no inventory, so every reader of it waits on the one dump rather than refusing while
     * the dump still writes.
     */
    private Path dumpedInventory() throws IOException, InterruptedException {
        dumpInventoryIfAbsent();
        return paths.inventoryFile();
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
