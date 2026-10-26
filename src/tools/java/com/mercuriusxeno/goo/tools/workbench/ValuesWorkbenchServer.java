package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.workbench.DraftSeed;
import com.mercuriusxeno.goo.workbench.DumpRunner;
import com.mercuriusxeno.goo.workbench.InventoryDump;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import com.mercuriusxeno.goo.workbench.WorkbenchReply;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * The values workbench's loopback server: the single-page app and each item's image, with every other route's body
 * answered by {@link WorkbenchOperations}. It lives in the tools source set behind the gradle valuesWorkbench run,
 * so the jar ships the in-game manager alone (decisions workbench-authors-base-values-whole,
 * web-page-moves-to-tools-and-leaves-the-jar).
 */
public final class ValuesWorkbenchServer {
    private static final String PAGE = "/workbench/index.html";
    private static final String NOTHING_POSTED = "";
    private static final String PLACEHOLDER = "/workbench/placeholder.png";
    private static final String HTML = "text/html; charset=utf-8";
    private static final String PNG = "image/png";
    private static final String GET = "GET";
    private static final String PUT = "PUT";
    private static final String POST = "POST";
    private static final String NDJSON = "application/x-ndjson; charset=utf-8";
    private static final String PROGRESS = "progress";
    private static final String RESULT = "result";
    private static final String ID_PARAMETER = "id=";
    private static final String QUERY_SEPARATOR = "&";
    // A namespaced item id: no segment may climb out of the image folder.
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+(/[a-z0-9_.-]+)*");
    private static final String PARENT_SEGMENT = "..";
    private static final String PNG_EXTENSION = ".png";
    private static final int OK = WorkbenchReply.OK;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int NOT_ALLOWED = 405;
    private static final int SERVER_ERROR = 500;
    private static final int STOP_GRACE_SECONDS = 2;
    // sendResponseHeaders reads -1 as a reply with no body.
    private static final int NO_BODY_LENGTH = -1;

    private static final String ROOT_PATH = "/";
    private static final String INDEX_PATH = "/index.html";
    private static final String API_PREFIX = "/api/";
    private static final String MODEL_ROUTE = "/api/model";
    private static final String IMAGE_ROUTE = "/api/image";
    private static final String RUN_DUMPS_ROUTE = "/api/dumps/run";
    private static final String STOP_ROUTE = "/api/stop";

    private static final String HTTP_SCHEME = "http://";
    // 127.0.0.1 rather than the JVM's preferred loopback: a game JVM preferring IPv6 binds ::1, whose bare host
    // string makes no URL a browser opens.
    private static final byte[] IPV4_LOOPBACK = {127, 0, 0, 1};
    private static final String PORT_SEPARATOR = ":";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String STOP_THREAD_NAME = "values-workbench-stop";
    private static final String STOPPING = "stopping";
    private static final String NOT_SERVED_HERE = " is not served here";
    private static final String EXCEPTION_MESSAGE_SEPARATOR = ": ";
    private static final String NO_PAGE_AT = "No page at ";
    private static final String NO_PAGE_RESOURCE = "The tools resources hold no ";
    private static final String NO_ITEM_NAMED = "Name an item: ?id=";
    private static final String NOT_AN_ITEM_ID = "Not an item id: ";
    private static final String NO_IMAGE_FOR = "No image for ";
    private static final String LINE_END = "\n";
    private static final String EMPTY_MODEL = "{\n}\n";

    private final HttpServer http;
    private final WorkbenchPaths paths;
    private final WorkbenchOperations operations;
    // Held so Stop can shut it down: its threads are not daemons, and one left alive keeps the JVM serving.
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean stopping = new AtomicBoolean();

    private ValuesWorkbenchServer(HttpServer http, WorkbenchPaths paths, WorkbenchOperations operations) {
        this.http = http;
        this.paths = paths;
        this.operations = operations;
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
        return start(paths, () -> EMPTY_MODEL.getBytes(StandardCharsets.UTF_8), dump, dumps, port);
    }

    /**
     * Binds the loopback address and starts serving, seeding a draft from the seed where no live file stands.
     *
     * @param paths the files the workbench reads and writes
     * @param seed  the text a draft starts from where no live file stands
     * @param dump  writes the inventory when none stands
     * @param dumps runs the audit and clique dumps
     * @param port  the port, or 0 for any free one
     * @return the running server
     * @throws IOException where the port cannot be bound
     */
    public static ValuesWorkbenchServer start(WorkbenchPaths paths, DraftSeed seed, InventoryDump dump,
                                              DumpRunner dumps, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(IPV4_LOOPBACK), port), 0);
        ValuesWorkbenchServer server = new ValuesWorkbenchServer(http, paths,
                // The gradle run holds no server, so the bundled types are the ones it knows
                // (decision draft-load-drops-entries-naming-an-unknown-type).
                new WorkbenchOperations(paths, seed, dump, dumps, () -> GooTypes.BUNDLED));
        http.createContext(ROOT_PATH, exchange -> server.answer(exchange, server::page));
        http.createContext(MODEL_ROUTE, exchange -> server.answer(exchange, server::model));
        http.createContext(IMAGE_ROUTE, exchange -> server.answer(exchange, server::image));
        server.serveAnyMethod(WorkbenchOperations.INVENTORY, WorkbenchOperations.STANDING_DUMPS,
                WorkbenchOperations.AUDIT_SECTIONS, WorkbenchOperations.RESOLVE_VALUES,
                WorkbenchOperations.UNCOVERED_VALUES, WorkbenchOperations.FAMILY, WorkbenchOperations.HOVER,
                WorkbenchOperations.PROCESSES);
        server.servePostOnly(WorkbenchOperations.EVALUATE_CONSTANTS, WorkbenchOperations.RENAME_CONSTANT,
                WorkbenchOperations.DELETE_CONSTANT, WorkbenchOperations.CONSTANT_HOVER,
                WorkbenchOperations.DESCRIBE_GROUPS, WorkbenchOperations.APPLY_TO_GROUP, WorkbenchOperations.PROMOTE,
                WorkbenchOperations.DISCARD);
        http.createContext(RUN_DUMPS_ROUTE, server::runDumps);
        http.createContext(STOP_ROUTE, exchange -> server.answer(exchange, server::stopRequested));
        http.setExecutor(server.executor);
        http.start();
        return server;
    }

    /**
     * Routes each operation at /api/ and its name, for any method.
     *
     * @param names the operations' names
     */
    private void serveAnyMethod(String... names) {
        for (String name : names) {
            http.createContext(API_PREFIX + name, exchange -> answer(exchange,
                    posted -> operations.answer(name, posted(posted))));
        }
    }

    /**
     * Routes each operation at /api/ and its name, refusing any method but POST.
     *
     * @param names the operations' names
     */
    private void servePostOnly(String... names) {
        for (String name : names) {
            http.createContext(API_PREFIX + name, exchange -> answer(exchange, posted -> POST.equals(
                    posted.getRequestMethod()) ? operations.answer(name, posted(posted)) : notAllowed(posted)));
        }
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
        operations.stopDumps();
        // The run's handler finishes its stream once the runner returns, so the stop waits on it briefly.
        http.stop(STOP_GRACE_SECONDS);
        executor.shutdownNow();
        stopped.countDown();
    }

    /**
     * @return whether the server has stopped, by the page's Stop or a call to stop
     */
    public boolean stopped() {
        return stopped.getCount() == 0;
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
    private WorkbenchReply stopRequested(HttpExchange exchange) {
        if (!POST.equals(exchange.getRequestMethod())) {
            return notAllowed(exchange);
        }
        operations.stopDumps();
        Thread stopper = new Thread(this::stop, STOP_THREAD_NAME);
        stopper.start();
        return WorkbenchReply.of(OK, WorkbenchReply.TEXT, STOPPING);
    }

    private interface Handler {
        WorkbenchReply handle(HttpExchange exchange) throws IOException, InterruptedException;
    }

    private static WorkbenchReply notAllowed(HttpExchange exchange) {
        return WorkbenchReply.of(NOT_ALLOWED, WorkbenchReply.TEXT, exchange.getRequestMethod() + NOT_SERVED_HERE);
    }

    private static String posted(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void answer(HttpExchange exchange, Handler handler) throws IOException {
        WorkbenchReply reply;
        try {
            reply = handler.handle(exchange);
        } catch (IllegalArgumentException e) {
            reply = WorkbenchReply.of(BAD_REQUEST, WorkbenchReply.TEXT, e.getMessage());
        } catch (RuntimeException | LinkageError e) {
            // Every request answers: an unanswered one leaves the page waiting with no word of the cause.
            reply = WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, e.getClass().getSimpleName()
                    + EXCEPTION_MESSAGE_SEPARATOR + e.getMessage());
        } catch (IOException e) {
            reply = WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply = WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, e.getMessage());
        }
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, reply.contentType());
        exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? NO_BODY_LENGTH : reply.body().length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(reply.body());
        }
    }

    private WorkbenchReply page(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!ROOT_PATH.equals(path) && !INDEX_PATH.equals(path)) {
            return WorkbenchReply.of(NOT_FOUND, WorkbenchReply.TEXT, NO_PAGE_AT + path);
        }
        try (InputStream page = ValuesWorkbenchServer.class.getResourceAsStream(PAGE)) {
            if (page == null) {
                return WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, NO_PAGE_RESOURCE + PAGE);
            }
            return new WorkbenchReply(OK, HTML, page.readAllBytes());
        }
    }

    private WorkbenchReply model(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (GET.equals(method)) {
            return operations.answer(WorkbenchOperations.MODEL, NOTHING_POSTED);
        }
        if (PUT.equals(method)) {
            return operations.answer(WorkbenchOperations.SAVE_MODEL, posted(exchange));
        }
        return notAllowed(exchange);
    }

    /**
     * Streams one JSON line per line the dump run prints, then a last line holding the run's result
     * (decision workbench-reruns-the-audit-headless).
     *
     * @param exchange the request carrying the model to save
     */
    private void runDumps(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            answer(exchange, ValuesWorkbenchServer::notAllowed);
            return;
        }
        String sent = posted(exchange);
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, NDJSON);
        exchange.sendResponseHeaders(OK, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            JsonObject result = operations.runDumps(sent, line -> streamLine(out, PROGRESS, new JsonPrimitive(line)));
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

    private WorkbenchReply image(HttpExchange exchange) throws IOException {
        String id = idParameter(exchange).orElseThrow(() -> new IllegalArgumentException(NO_ITEM_NAMED));
        if (!ITEM_ID.matcher(id).matches() || id.contains(PARENT_SEGMENT)) {
            throw new IllegalArgumentException(NOT_AN_ITEM_ID + id);
        }
        int colon = id.indexOf(':');
        Path image = paths.imageFolder().resolve(id.substring(0, colon))
                .resolve(id.substring(colon + 1) + PNG_EXTENSION);
        if (!Files.isRegularFile(image)) {
            return placeholder(id);
        }
        return new WorkbenchReply(OK, PNG, Files.readAllBytes(image));
    }

    /**
     * An item no capture has drawn yet, on a page mid-capture or on a dedicated server, draws a slot-shaped blank.
     * decision images-are-slot-captures-taken-on-detection
     *
     * @param id the uncaptured item's id
     * @return the placeholder image
     */
    private static WorkbenchReply placeholder(String id) throws IOException {
        try (InputStream blank = ValuesWorkbenchServer.class.getResourceAsStream(PLACEHOLDER)) {
            if (blank == null) {
                return WorkbenchReply.of(NOT_FOUND, WorkbenchReply.TEXT, NO_IMAGE_FOR + id);
            }
            return new WorkbenchReply(OK, PNG, blank.readAllBytes());
        }
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
