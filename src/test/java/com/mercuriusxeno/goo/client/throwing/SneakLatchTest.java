package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Once a link lands, the sneak still held stops counting until it is let go
 * and pressed again: no portal lights up as aimed at, and no second link
 * starts on the same press (decision portals-link-by-looking-through-portal-vision).
 */
class SneakLatchTest {

    @Test
    void aSneakHeldThroughALinkStopsCounting() {
        SneakLatch latch = new SneakLatch();
        latch.update(true);
        assertTrue(latch.sneaking(true), "A sneak before any link should count");
        latch.linked();
        latch.update(true);
        assertFalse(latch.sneaking(true), "The sneak held through a link should stop counting");
    }

    @Test
    void lettingGoAndSneakingAgainCountsAgain() {
        SneakLatch latch = new SneakLatch();
        latch.linked();
        latch.update(true);
        latch.update(false);
        latch.update(true);
        assertTrue(latch.sneaking(true), "A sneak pressed again after letting go should count");
    }
}
