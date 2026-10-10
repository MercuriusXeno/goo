package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A held ability's visual runs only while right click holds it and goo of the
 * selected type is left, so a channel out of goo stops drawing its fog, breeze,
 * vortex or cursor as its stream stops.
 */
class GloveUseTrackerTest {

    @Test
    void aHeldAbilityWithGooLeftRunsItsVisual() {
        assertTrue(GloveUseTracker.heldVisualRuns(true, true));
    }

    @Test
    void aHeldAbilityOutOfGooDrawsNothing() {
        assertFalse(GloveUseTracker.heldVisualRuns(true, false));
    }

    @Test
    void aReleasedAbilityDrawsNothing() {
        assertFalse(GloveUseTracker.heldVisualRuns(false, true));
    }
}
