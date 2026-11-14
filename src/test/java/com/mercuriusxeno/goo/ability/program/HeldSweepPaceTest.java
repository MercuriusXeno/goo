package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A held sweep's front leaves the caster at one steady pace, 48 blocks a
 * second, with no ease and no rest, landing Scry's 96 blocks on tick 40 and
 * Glimmer's and Magnet's 64 on tick 27; and a press starts a pulse at once
 * and a hold another every second (operator ruling 2026-11-06: fewer pulses).
 * decision held-sweeps-share-one-fast-shell-visual
 */
class HeldSweepPaceTest {

    private static final HeldSweepPace SCRY = new HeldSweepPace(96);
    private static final HeldSweepPace GLIMMER = new HeldSweepPace(64);
    /** 48 blocks a second, twenty ticks to the second. */
    private static final double BLOCKS_A_TICK = 2.4;
    private static final double EXACT = 1e-9;

    @Test
    void aFrontAdvancesTheSameBlocksEveryTickUntilItsRange() {
        for (int tick = 1; tick <= SCRY.ticksToRange(); tick++) {
            assertEquals(BLOCKS_A_TICK, SCRY.radiusAt(tick) - SCRY.radiusAt(tick - 1), EXACT, "tick " + tick);
        }
        assertEquals(SCRY.range(), SCRY.radiusAt(SCRY.ticksToRange() + 1), EXACT);
    }

    @Test
    void scrysFrontLandsOnTickFortyAndGlimmersOnTickTwentySeven() {
        assertEquals(40, SCRY.ticksToRange());
        assertEquals(27, GLIMMER.ticksToRange());
        assertEquals(SCRY.range(), SCRY.radiusAt(40), EXACT);
        assertTrue(SCRY.radiusAt(39) < SCRY.range());
        assertEquals(GLIMMER.range(), GLIMMER.radiusAt(27), EXACT);
        assertTrue(GLIMMER.radiusAt(26) < GLIMMER.range());
    }

    @Test
    void aPressStartsAPulseAtOnceAndAHoldAnotherEverySecond() {
        assertTrue(SCRY.startsPulse(1), "the press");
        for (int held = 2; held <= 20; held++) {
            assertFalse(SCRY.startsPulse(held), "held " + held);
        }
        assertTrue(SCRY.startsPulse(21), "a second into the hold");
        assertTrue(SCRY.startsPulse(41), "two seconds into the hold");
    }
}
