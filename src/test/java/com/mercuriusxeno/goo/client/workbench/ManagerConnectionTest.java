package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.network.WorkbenchReplyPayload;
import com.mercuriusxeno.goo.network.WorkbenchRequestPayload;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the manager's connection sends each request under its own id and completes a request's future from
 * the reply carrying that id alone (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class ManagerConnectionTest {

    /**
     * Two requests in flight each complete from the reply naming their id, whatever order the replies arrive in.
     */
    @Test
    void replyCompletesTheRequestCarryingItsId() {
        List<WorkbenchRequestPayload> sent = new ArrayList<>();
        ManagerConnection connection = new ManagerConnection(sent::add);
        CompletableFuture<ManagerConnection.ManagerReply> model = connection.request("model", "");
        CompletableFuture<ManagerConnection.ManagerReply> inventory = connection.request("inventory", "");
        assertEquals(List.of("model", "inventory"), sent.stream().map(WorkbenchRequestPayload::operation).toList());
        int inventoryId = sent.get(1).requestId();
        connection.accept(new WorkbenchReplyPayload(inventoryId, 200, 0, 1, "{\"items\":[]}"));
        assertTrue(inventory.isDone());
        assertFalse(model.isDone());
        assertEquals("{\"items\":[]}", inventory.join().text());
        connection.accept(new WorkbenchReplyPayload(sent.get(0).requestId(), 403, 0, 1, "refused"));
        assertFalse(model.join().answered());
    }

    /**
     * A reply in parts completes its request only once the last part arrives, joined in order.
     */
    @Test
    void partedReplyCompletesOnItsLastPart() {
        List<WorkbenchRequestPayload> sent = new ArrayList<>();
        ManagerConnection connection = new ManagerConnection(sent::add);
        CompletableFuture<ManagerConnection.ManagerReply> reply = connection.request("inventory", "");
        int id = sent.getFirst().requestId();
        connection.accept(new WorkbenchReplyPayload(id, 200, 1, 2, "second"));
        assertFalse(reply.isDone());
        connection.accept(new WorkbenchReplyPayload(id, 200, 0, 2, "first-"));
        assertEquals("first-second", reply.join().text());
    }

    /**
     * A dump run's progress parts reach its progress consumer in order and leave the request open, and the part
     * after them completes it with the run's exit code.
     */
    @Test
    void progressPartsReachTheConsumerInOrderBeforeTheResult() {
        List<WorkbenchRequestPayload> sent = new ArrayList<>();
        List<String> progress = new ArrayList<>();
        ManagerConnection connection = new ManagerConnection(sent::add);
        CompletableFuture<ManagerConnection.ManagerReply> run = connection.request("dumps/run", "{}", progress::add);
        int id = sent.getFirst().requestId();
        connection.accept(new WorkbenchReplyPayload(id, 102, 0, 1, "booting"));
        connection.accept(new WorkbenchReplyPayload(id, 102, 0, 1, "auditing"));
        assertFalse(run.isDone());
        connection.accept(new WorkbenchReplyPayload(id, 200, 0, 1, "{\"exitCode\":0}"));
        assertEquals(List.of("booting", "auditing"), progress);
        assertEquals("{\"exitCode\":0}", run.join().text());
    }
}
