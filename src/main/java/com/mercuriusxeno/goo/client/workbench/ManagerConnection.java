package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.network.WorkbenchPayloadParts;
import com.mercuriusxeno.goo.network.WorkbenchReplyPayload;
import com.mercuriusxeno.goo.network.WorkbenchRequestPayload;
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
    private final Consumer<WorkbenchRequestPayload> transport;
    private final AtomicInteger nextId = new AtomicInteger();
    private final Map<Integer, CompletableFuture<ManagerReply>> waiting = new ConcurrentHashMap<>();
    private final WorkbenchPayloadParts joiner = new WorkbenchPayloadParts();

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
        int id = nextId.incrementAndGet();
        CompletableFuture<ManagerReply> reply = new CompletableFuture<>();
        waiting.put(id, reply);
        WorkbenchRequestPayload.parts(id, operation, text).forEach(transport);
        return reply;
    }

    /**
     * Takes one reply part; the last part of a reply completes the request carrying its id.
     *
     * @param part the reply part
     */
    public void accept(WorkbenchReplyPayload part) {
        joiner.accept(Integer.toString(part.requestId()), part.part(), part.parts(), part.text()).ifPresent(text -> {
            CompletableFuture<ManagerReply> reply = waiting.remove(part.requestId());
            if (reply != null) {
                reply.complete(new ManagerReply(part.status(), text));
            }
        });
    }
}
