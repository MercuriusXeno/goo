package com.mercuriusxeno.goo.ability.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScryStep's pings: a ping runs a block a tick to its reach and on through
 * its fade, then the next starts; and the distances one tick's front crosses.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
class ScryStepTest {

    private static final ScryStep SCRY = new ScryStep(1, 96, 20, List.of(), List.of());
    private static final int PING_TICKS = 116;

    @Test
    void theHoldsFirstTickStartsThePing() {
        assertEquals(1, SCRY.pingTick(1));
    }

    @Test
    void aPingRunsThroughItsReachAndFadeBeforeTheNextStarts() {
        assertEquals(96, SCRY.pingTick(96));
        assertEquals(PING_TICKS, SCRY.pingTick(PING_TICKS));
        assertEquals(1, SCRY.pingTick(PING_TICKS + 1));
    }

    @Test
    void frontCrossesWhatLiesPastLastTicksRadiusAndWithinThisOnes() {
        assertTrue(ScryStep.crossed(4.5, 4, 5));
        assertTrue(ScryStep.crossed(5, 4, 5));
        assertFalse(ScryStep.crossed(4, 4, 5));
        assertFalse(ScryStep.crossed(5.1, 4, 5));
    }
}
