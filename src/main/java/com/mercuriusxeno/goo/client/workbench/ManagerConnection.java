package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.network.WorkbenchPayloadParts;
import com.mercuriusxeno.goo.network.WorkbenchReplyPayload;
import com.mercuriusxeno.goo.network.WorkbenchRequestPayload;
import com.mercuriusxeno.goo.network.WorkbenchRequests;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The values manager's line to the server: each request goes out in parts under a fresh id, and the reply carrying
 * that id completes the request's future once its last part arrives
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class ManagerConnection {
    private static final String PROGRESS_KEY = "progress ";

    private final Consumer<WorkbenchRequestPayload> transport;
    private final AtomicInteger nextId = new AtomicInteger();
    private final Map<Integer, Waiting> waiting = new ConcurrentHashMap<>();
    private final WorkbenchPayloadParts joiner = new WorkbenchPayloadParts();

    private record Waiting(CompletableFuture<ManagerReply> reply, Consumer<String> progress) {
    }

    /**
     * @param transport sends one request part to the server
     */
    public ManagerConnection(Consumer<WorkbenchRequestPayload> transport) {
        this.transport = transport;
    }

    /**
     * An operation's reply as the manager reads it.
     *
     * @param status the reply's status, 200 for an answer
     * @param text   the reply's text
     */
    public record ManagerReply(int status, String text) {
        private static final int OK = 200;

        /**
         * @return whether the server answered rather than refused
         */
        public boolean answered() {
            return status == OK;
        }
    }

    /**
     * @param operation the workbench operation's name
     * @param text      the posted text, empty for a read
     * @return the reply, once its last part arrives
     */
    public CompletableFuture<ManagerReply> request(String operation, String text) {
        return request(operation, text, line -> { });
    }

    /**
     * @param operation the workbench operation's name
     * @param text      the posted text
     * @param progress  takes each progress line the operation sends before its reply, in order
     * @return the reply, once its last part arrives
     */
    public CompletableFuture<ManagerReply> request(String operation, String text, Consumer<String> progress) {
        int id = nextId.incrementAndGet();
        CompletableFuture<ManagerReply> reply = new CompletableFuture<>();
        waiting.put(id, new Waiting(reply, progress));
        WorkbenchRequestPayload.parts(id, operation, text).forEach(transport);
        return reply;
    }

    /**
     * Takes one reply part: a progress part's line goes to its request's progress, and the last part of a reply
     * completes the request carrying its id.
     *
     * @param part the reply part
     */
    public void accept(WorkbenchReplyPayload part) {
        boolean progress = part.status() == WorkbenchRequests.PROGRESS;
        String key = progress ? PROGRESS_KEY + part.requestId() : Integer.toString(part.requestId());
        joiner.accept(key, part.part(), part.parts(), part.text()).ifPresent(text -> {
            Waiting request = progress ? waiting.get(part.requestId()) : waiting.remove(part.requestId());
            if (request == null) {
                return;
            }
            if (progress) {
                request.progress().accept(text);
            } else {
                request.reply().complete(new ManagerReply(part.status(), text));
            }
        });
    }
}
