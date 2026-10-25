package com.mercuriusxeno.goo.network;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the values manager's request seam with a fake sender: a sender lacking op is refused and runs nothing, and an
 * operator's parted request runs once on its last part (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class WorkbenchRequestsTest {

    private static final class FakeSender implements WorkbenchRequests.Sender {
        private final boolean operator;
        private final List<WorkbenchReplyPayload> replies = new ArrayList<>();

        FakeSender(boolean operator) {
            this.operator = operator;
        }

        @Override
        public String key() {
            return "player";
        }

        @Override
        public boolean mayManageValues() {
            return operator;
        }

        @Override
        public void send(WorkbenchReplyPayload part) {
            replies.add(part);
        }
    }

    /**
     * A sender lacking op is answered with the refusal under its request id, and no operation runs.
     */
    @Test
    void senderLackingOpIsRefusedAndRunsNothing() {
        AtomicInteger ran = new AtomicInteger();
        FakeSender sender = new FakeSender(false);
        new WorkbenchRequests(Runnable::run).accept(sender, new WorkbenchRequestPayload(5, "model", 0, 1, ""),
                (operation, text) -> {
                    ran.incrementAndGet();
                    return new WorkbenchRequests.Answer(200, "{}");
                });
        assertEquals(0, ran.get());
        assertEquals(List.of(new WorkbenchReplyPayload(5, WorkbenchRequests.FORBIDDEN, 0, 1,
                WorkbenchRequests.REFUSED)), sender.replies);
    }

    /**
     * An operator's request in parts runs once, on its last part, over the joined text, and its answer comes back
     * under the request's id.
     */
    @Test
    void operatorsPartedRequestRunsOnceOverTheJoinedText() {
        List<String> ran = new ArrayList<>();
        FakeSender sender = new FakeSender(true);
        WorkbenchRequests requests = new WorkbenchRequests(Runnable::run);
        WorkbenchRequests.Runner runner = (operation, text) -> {
            ran.add(operation + " " + text);
            return new WorkbenchRequests.Answer(200, "saved");
        };
        requests.accept(sender, new WorkbenchRequestPayload(8, "model/save", 0, 2, "{\"members\""), runner);
        assertEquals(List.of(), ran);
        requests.accept(sender, new WorkbenchRequestPayload(8, "model/save", 1, 2, ":[]}"), runner);
        assertEquals(List.of("model/save {\"members\":[]}"), ran);
        assertEquals(List.of(new WorkbenchReplyPayload(8, 200, 0, 1, "saved")), sender.replies);
    }
}
