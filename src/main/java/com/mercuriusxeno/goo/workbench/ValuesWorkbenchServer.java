package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The values workbench's loopback server: the single-page app, the draft beside base_values.json read and
 * saved, Promote and Discard between them, the inventory, and each item's image (decisions
 * workbench-authors-base-values-whole, workbench-authors-a-draft-and-promotes-by-rename).
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
    private static final String GROUPS = "groups";
    private static final String EXIT_CODE = "exitCode";
    private static final String DURATION = "durationSeconds";
    private static final double NANOS_PER_SECOND = 1e9;
    private static final String ID_PARAMETER = "id=";
    private static final String QUERY_SEPARATOR = "&";
    // A namespaced item id: no segment may climb out of the image folder.
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+(/[a-z0-9_.-]+)*");
    private static final String PARENT_SEGMENT = "..";
    private static final String PNG_EXTENSION = ".png";
    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int NOT_ALLOWED = 405;
    private static final int SERVER_ERROR = 500;
    private static final int STOP_GRACE_SECONDS = 2;
    // sendResponseHeaders reads -1 as a reply with no body.
    private static final int NO_BODY_LENGTH = -1;
    // The exit code a run reports when it was interrupted or could not start, since the runner gave none.
    private static final int RUN_FAILED_EXIT = -1;

    private static final String ROOT_PATH = "/";
    private static final String INDEX_PATH = "/index.html";
    private static final String MODEL_ROUTE = "/api/model";
    private static final String INVENTORY_ROUTE = "/api/inventory";
    private static final String IMAGE_ROUTE = "/api/image";
    private static final String EVALUATE_CONSTANTS_ROUTE = "/api/constants/evaluate";
    private static final String RENAME_CONSTANT_ROUTE = "/api/constants/rename";
    private static final String DELETE_CONSTANT_ROUTE = "/api/constants/delete";
    private static final String DESCRIBE_GROUPS_ROUTE = "/api/groups/describe";
    private static final String APPLY_TO_GROUP_ROUTE = "/api/groups/apply";
    private static final String DUMPS_ROUTE = "/api/dumps";
    private static final String RUN_DUMPS_ROUTE = "/api/dumps/run";
    private static final String AUDIT_ROUTE = "/api/audit";
    private static final String RESOLVE_VALUES_ROUTE = "/api/values/resolve";
    private static final String UNCOVERED_VALUES_ROUTE = "/api/values/uncovered";
    private static final String FAMILY_ROUTE = "/api/values/family";
    private static final String HOVER_ROUTE = "/api/hover";
    private static final String PROCESSES_ROUTE = "/api/processes";
    private static final String STOP_ROUTE = "/api/stop";
    private static final String PROMOTE_ROUTE = "/api/promote";
    private static final String DISCARD_ROUTE = "/api/discard";
    private static final int CONFLICT = 409;
    private static final String NO_DRAFT_AT = "No draft stands to promote at ";
    private static final String NO_LIVE_FILE_AT = "No live file stands to discard back to at ";

    private static final String HTTP_SCHEME = "http://";
    private static final String PORT_SEPARATOR = ":";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String STOP_THREAD_NAME = "values-workbench-stop";
    private static final String STOPPING = "stopping";
    private static final String NOT_SERVED_HERE = " is not served here";
    private static final String EXCEPTION_MESSAGE_SEPARATOR = ": ";
    private static final String NO_PAGE_AT = "No page at ";
    private static final String NO_PAGE_RESOURCE = "The tools resources hold no ";
    private static final String NO_INVENTORY_WRITTEN = "The inventory dump wrote no ";
    private static final String NO_ITEM_NAMED = "Name an item: ?id=";
    private static final String NOT_AN_ITEM_ID = "Not an item id: ";
    private static final String NO_IMAGE_FOR = "No image for ";
    private static final String LINE_END = "\n";
    private static final String EMPTY_MODEL = "{\n}\n";

    private final HttpServer http;
    private final WorkbenchPaths paths;
    private final InventoryDump dump;
    private final DumpRunner dumps;
    // Held so Stop can shut it down: its threads are not daemons, and one left alive keeps the JVM serving.
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean stopping = new AtomicBoolean();
    // Guards the draft file alone: a dump run holds the server's own monitor for minutes, and a seed, Promote
    // or Discard must not wait on it.
    private final Object draftLock = new Object();

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
        http.createContext(ROOT_PATH, exchange -> server.answer(exchange, server::page));
        http.createContext(MODEL_ROUTE, exchange -> server.answer(exchange, server::model));
        http.createContext(INVENTORY_ROUTE, exchange -> server.answer(exchange, server::inventory));
        http.createContext(IMAGE_ROUTE, exchange -> server.answer(exchange, server::image));
        http.createContext(EVALUATE_CONSTANTS_ROUTE, exchange -> server.answer(exchange, server::evaluateConstants));
        http.createContext(RENAME_CONSTANT_ROUTE, exchange -> server.answer(exchange, server::renameConstant));
        http.createContext(DELETE_CONSTANT_ROUTE, exchange -> server.answer(exchange, server::deleteConstant));
        http.createContext(DESCRIBE_GROUPS_ROUTE, exchange -> server.answer(exchange, server::describeGroups));
        http.createContext(APPLY_TO_GROUP_ROUTE, exchange -> server.answer(exchange, server::applyToGroup));
        http.createContext(DUMPS_ROUTE, exchange -> server.answer(exchange, server::standingDumps));
        http.createContext(RUN_DUMPS_ROUTE, server::runDumps);
        http.createContext(AUDIT_ROUTE, exchange -> server.answer(exchange, server::auditSections));
        http.createContext(RESOLVE_VALUES_ROUTE, exchange -> server.answer(exchange,
                posted -> Reply.of(OK, JSON, Files.isRegularFile(paths.inventoryFile())
                        ? Valuations.resolved(server.postedModel(posted), ValuesJson.inventory(paths.inventoryFile()))
                        : Valuations.resolved(server.postedModel(posted)))));
        http.createContext(UNCOVERED_VALUES_ROUTE, exchange -> server.answer(exchange,
                posted -> Reply.of(OK, JSON, ValuesJson.uncovered(server.postedModel(posted),
                        ValuesJson.inventory(server.dumpedInventory())))));
        http.createContext(FAMILY_ROUTE, exchange -> server.answer(exchange, server::family));
        http.createContext(HOVER_ROUTE, exchange -> server.answer(exchange, server::hover));
        http.createContext(PROCESSES_ROUTE, exchange -> server.answer(exchange,
                posted -> Reply.of(OK, JSON, ProcessesJson.described(server.postedModel(posted)))));
        http.createContext(PROMOTE_ROUTE, exchange -> server.answer(exchange, server::promote));
        http.createContext(DISCARD_ROUTE, exchange -> server.answer(exchange, server::discard));
        http.createContext(STOP_ROUTE, exchange -> server.answer(exchange, server::stopRequested));
        http.setExecutor(server.executor);
        http.start();
        return server;
    }

    /**
     * @return the address the page opens at
     */
    public String url() {
        return HTTP_SCHEME + http.getAddress().getHostString() + PORT_SEPARATOR + http.getAddress().getPort();
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
     *
     * @param exchange the stop request
     * @return OK, or not allowed for any method but POST
     */
    private Reply stopRequested(HttpExchange exchange) {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        dumps.stop();
        Thread stopper = new Thread(this::stop, STOP_THREAD_NAME);
        stopper.start();
        return Reply.of(OK, TEXT, STOPPING);
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
            reply = Reply.of(SERVER_ERROR, TEXT, e.getClass().getSimpleName() + EXCEPTION_MESSAGE_SEPARATOR
                    + e.getMessage());
        } catch (IOException e) {
            reply = Reply.of(SERVER_ERROR, TEXT, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply = Reply.of(SERVER_ERROR, TEXT, e.getMessage());
        }
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, reply.contentType());
        exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? NO_BODY_LENGTH : reply.body().length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(reply.body());
        }
    }

    private Reply page(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!ROOT_PATH.equals(path) && !INDEX_PATH.equals(path)) {
            return Reply.of(NOT_FOUND, TEXT, NO_PAGE_AT + path);
        }
        try (InputStream page = ValuesWorkbenchServer.class.getResourceAsStream(PAGE)) {
            if (page == null) {
                return Reply.of(SERVER_ERROR, TEXT, NO_PAGE_RESOURCE + PAGE);
            }
            return new Reply(OK, HTML, page.readAllBytes());
        }
    }

    private Reply model(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (GET.equals(method)) {
            return modelReply(fileLayout());
        }
        if (PUT.equals(method)) {
            String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            BaseValuesModel saved = fileLayout().withMembers(ModelJson.membersFrom(sent));
            Files.writeString(paths.draft(), saved.serialize());
            return modelReply(saved);
        }
        return Reply.of(NOT_ALLOWED, TEXT, method + NOT_SERVED_HERE);
    }

    /**
     * Resolves the posted model's constants through the game's parser, each with its error and its readers.
     *
     * @param exchange the request carrying the model
     * @return the constants evaluated, or not allowed for any method but POST
     */
    private Reply evaluateConstants(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        BaseValuesModel model = postedModel(exchange);
        return Reply.of(OK, JSON, ConstantsJson.evaluated(model));
    }

    /**
     * Renames a constant in the posted model and answers the model with every reference rewritten.
     *
     * @param exchange the request carrying the rename and the model
     * @return the renamed model, or not allowed for any method but POST
     */
    private Reply renameConstant(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        ConstantsJson.Rename rename = ConstantsJson.renameFrom(sent);
        BaseValuesModel renamed = fileLayout().withMembers(ModelJson.membersFrom(sent))
                .renameConstant(rename.from(), rename.to());
        return modelReply(renamed);
    }

    /**
     * Deletes a constant from the posted model with its cascade of readers and answers the model left
     * (decision deleting-a-variable-inlines-its-expression-into-each-reader).
     *
     * @param exchange the request carrying the constant's name and the model
     * @return the model left, or not allowed for any method but POST
     */
    private Reply deleteConstant(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        BaseValuesModel deleted = fileLayout().withMembers(ModelJson.membersFrom(sent))
                .deleteConstant(ConstantsJson.deleteFrom(sent));
        return modelReply(deleted);
    }

    /**
     * Describes every group of the posted model against the inventory.
     *
     * @param exchange the request carrying the model
     * @return the groups described, or not allowed for any method but POST
     */
    private Reply describeGroups(HttpExchange exchange) throws IOException, InterruptedException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        JsonObject answer = new JsonObject();
        answer.add(GROUPS, GroupsJson.described(postedModel(exchange), GroupsJson.Inventory.read(
                dumpedInventory())));
        return Reply.of(OK, JSON, answer.toString());
    }

    /**
     * Applies one change to a group of the posted model and answers the model with every group described.
     *
     * @param exchange the request carrying the change and the model
     * @return the changed model with its groups, or not allowed for any method but POST
     */
    private Reply applyToGroup(HttpExchange exchange) throws IOException, InterruptedException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Reply.of(OK, JSON, GroupsJson.applied(sent, fileLayout(), GroupsJson.Inventory.read(
                dumpedInventory())));
    }

    /**
     * Answers the sections goo_audit.json carries, each rule's section, lines and flags, read afresh on every
     * request so a rule added later shows with no restart (decision each-check-is-one-rule-added-alone).
     *
     * @param exchange the request
     * @return the audit's sections, or none where no audit stands
     */
    private Reply auditSections(HttpExchange exchange) throws IOException {
        if (!Files.isRegularFile(paths.auditJson())) {
            JsonObject none = new JsonObject();
            none.add(SECTIONS, new JsonArray());
            return Reply.of(OK, JSON, none.toString());
        }
        return new Reply(OK, JSON, Files.readAllBytes(paths.auditJson()));
    }

    /**
     * Answers the dumps the last run left, section by section, with no run.
     *
     * @param exchange the request
     * @return the standing dumps' sections
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
     *
     * @param exchange the request carrying the model to save
     */
    private synchronized void runDumps(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            answer(exchange, ignored -> Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod()
                    + NOT_SERVED_HERE));
            return;
        }
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        BaseValuesModel saved = fileLayout().withMembers(ModelJson.membersFrom(sent));
        Files.writeString(paths.draft(), saved.serialize());
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, NDJSON);
        exchange.sendResponseHeaders(OK, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            long start = System.nanoTime();
            int exit;
            try {
                exit = dumps.run(paths.draft(), line -> streamLine(out, PROGRESS, new JsonPrimitive(line)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exit = RUN_FAILED_EXIT;
            } catch (IOException e) {
                streamLine(out, PROGRESS, new JsonPrimitive(e.getMessage()));
                exit = RUN_FAILED_EXIT;
            }
            JsonObject result = new JsonObject();
            result.addProperty(EXIT_CODE, exit);
            result.addProperty(DURATION, (System.nanoTime() - start) / NANOS_PER_SECOND);
            result.add(SECTIONS, DumpSections.read(paths.auditDump(), paths.cliqueDump()));
            streamLine(out, RESULT, result);
        }
    }

    private static void streamLine(OutputStream out, String field, JsonElement value) {
        JsonObject line = new JsonObject();
        line.add(field, value);
        try {
            out.write((line + LINE_END).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            // The page closed the stream; the run carries on and its dumps still land.
        }
    }

    /**
     * Answers the Values table for the posted item's family, valued from the posted model.
     *
     * @param exchange the request carrying the item's id and the model
     * @return the family's Values table
     */
    private Reply family(HttpExchange exchange) throws IOException, InterruptedException {
        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        BaseValuesModel model = fileLayout().withMembers(ModelJson.membersFrom(sent));
        return Reply.of(OK, JSON, Valuations.family(model, ValuesJson.inventory(dumpedInventory()),
                ValuesJson.idFrom(sent)));
    }

    /**
     * Answers the posted item's hover card, valued from the posted model.
     *
     * @param exchange the request carrying the item's id and the model
     * @return the item's hover card
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

    /**
     * @return the draft's model, the draft seeded first where none stands
     */
    private BaseValuesModel fileLayout() throws IOException {
        return BaseValuesModel.parse(Files.readString(seededDraft()));
    }

    /**
     * Seeds the draft from the live file where no draft stands, or from an empty model where no live file
     * stands either, so every read and save works on the draft (decision workbench-authors-a-draft-and-promotes-by-rename).
     *
     * @return the draft's path
     */
    private Path seededDraft() throws IOException {
        Path draft = paths.draft();
        synchronized (draftLock) {
            if (Files.isRegularFile(draft)) {
                return draft;
            }
            Files.createDirectories(draft.getParent());
            if (Files.isRegularFile(paths.baseValues())) {
                Files.copy(paths.baseValues(), draft);
            } else {
                Files.writeString(draft, EMPTY_MODEL);
            }
        }
        return draft;
    }

    private boolean draftDiffers() throws IOException {
        Path draft = paths.draft();
        return Files.isRegularFile(draft)
                && (!Files.isRegularFile(paths.baseValues()) || Files.mismatch(draft, paths.baseValues()) >= 0);
    }

    private Reply modelReply(BaseValuesModel model) throws IOException {
        return Reply.of(OK, JSON, ModelJson.toJson(model, paths.baseValues().toString(), paths.draft().toString(),
                draftDiffers()));
    }

    /**
     * Renames the draft over the live file, the one write the live file takes, and answers the live model; the
     * next read seeds a fresh draft (decision workbench-authors-a-draft-and-promotes-by-rename).
     *
     * @param exchange the promote request
     * @return the promoted model, or a conflict where no draft stands
     */
    private Reply promote(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        synchronized (draftLock) {
            if (!Files.isRegularFile(paths.draft())) {
                return Reply.of(CONFLICT, TEXT, NO_DRAFT_AT + paths.draft());
            }
            Files.move(paths.draft(), paths.baseValues(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        return modelReply(BaseValuesModel.parse(Files.readString(paths.baseValues())));
    }

    /**
     * Copies the live file back over the draft, dropping every unpromoted change
     * (decision workbench-authors-a-draft-and-promotes-by-rename).
     *
     * @param exchange the discard request
     * @return the live model, now the draft's, or a conflict where no live file stands
     */
    private Reply discard(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            return Reply.of(NOT_ALLOWED, TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
        }
        synchronized (draftLock) {
            if (!Files.isRegularFile(paths.baseValues())) {
                return Reply.of(CONFLICT, TEXT, NO_LIVE_FILE_AT + paths.baseValues());
            }
            Files.copy(paths.baseValues(), paths.draft(), StandardCopyOption.REPLACE_EXISTING);
        }
        return modelReply(BaseValuesModel.parse(Files.readString(paths.draft())));
    }

    private Reply inventory(HttpExchange exchange) throws IOException, InterruptedException {
        dumpInventoryIfAbsent();
        if (!Files.isRegularFile(paths.inventoryFile())) {
            return Reply.of(SERVER_ERROR, TEXT, NO_INVENTORY_WRITTEN + paths.inventoryFile());
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
     *
     * @return the inventory file, dumped first where none stands
     */
    private Path dumpedInventory() throws IOException, InterruptedException {
        dumpInventoryIfAbsent();
        return paths.inventoryFile();
    }

    private Reply image(HttpExchange exchange) throws IOException {
        String id = idParameter(exchange).orElseThrow(() -> new IllegalArgumentException(NO_ITEM_NAMED));
        if (!ITEM_ID.matcher(id).matches() || id.contains(PARENT_SEGMENT)) {
            throw new IllegalArgumentException(NOT_AN_ITEM_ID + id);
        }
        int colon = id.indexOf(':');
        Path image = paths.imageFolder().resolve(id.substring(0, colon))
                .resolve(id.substring(colon + 1) + PNG_EXTENSION);
        if (!Files.isRegularFile(image)) {
            return Reply.of(NOT_FOUND, TEXT, NO_IMAGE_FOR + id);
        }
        return new Reply(OK, PNG, Files.readAllBytes(image));
    }

    private static Optional<String> idParameter(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return Optional.empty();
        }
        for (String pair : query.split(QUERY_SEPARATOR)) {
            if (pair.startsWith(ID_PARAMETER)) {
                return Optional.of(URLDecoder.decode(pair.substring(ID_PARAMETER.length()), StandardCharsets.UTF_8));
            }
        }
        return Optional.empty();
    }
}
