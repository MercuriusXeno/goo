package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A held sweep's front leaves the caster at its step's own steady pace,
 * with no ease and no rest: the shipped Scry's crosses its 32 blocks in two
 * seconds, landing on tick 40, while Glimmer's and Magnet's keep 48 blocks
 * a second, landing their 64 on tick 27; and a press starts a pulse at once
 * and a hold another every second (operator ruling 2026-11-06: fewer pulses).
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-ring-crosses-its-reach-in-two-seconds
 */
class HeldSweepPaceTest {

    private static final HeldSweepPace SCRY = paceOf("astral_scry");
    private static final HeldSweepPace GLIMMER = paceOf("crystal_glimmer");
    private static final HeldSweepPace MAGNET = paceOf("metal_magnet");
    /** 32 blocks in 40 ticks. */
    private static final double SCRY_BLOCKS_A_TICK = 0.8;
    private static final double EXACT = 1e-9;

    @Test
    void aFrontAdvancesTheSameBlocksEveryTickUntilItsRange() {
        for (int tick = 1; tick <= SCRY.ticksToRange(); tick++) {
            assertEquals(SCRY_BLOCKS_A_TICK, SCRY.radiusAt(tick) - SCRY.radiusAt(tick - 1), EXACT, "tick " + tick);
        }
        assertEquals(SCRY.range(), SCRY.radiusAt(SCRY.ticksToRange() + 1), EXACT);
    }

    @Test
    void scrysFrontIsHalfwayAtOneSecondAndAtItsThirtyTwoBlocksAtTwo() {
        assertEquals(16, SCRY.radiusAt(20), EXACT, "a second out");
        assertEquals(32, SCRY.radiusAt(40), EXACT, "two seconds out");
        assertEquals(40, SCRY.ticksToRange());
        assertTrue(SCRY.radiusAt(39) < SCRY.range());
    }

    /**
     * Glimmer and Magnet keep the pace they had before Scry slowed, since
     * they leave the sweep on a thread of their own.
     * decision scry-ring-crosses-its-reach-in-two-seconds
     */
    @Test
    void glimmersAndMagnetsFrontsTravelFortyEightBlocksASecond() {
        assertEquals(48, GLIMMER.radiusAt(20), EXACT, "glimmer");
        assertEquals(48, MAGNET.radiusAt(20), EXACT, "magnet");
        assertEquals(27, GLIMMER.ticksToRange());
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

    private static HeldSweepPace paceOf(String ability) {
        return AbilityJson.decode(ability).behaviors().stream().filter(HeldSweepStep.class::isInstance)
                .map(HeldSweepStep.class::cast).findFirst().orElseThrow().pace();
    }
}
