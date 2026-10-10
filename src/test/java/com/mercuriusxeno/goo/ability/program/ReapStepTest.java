package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reap's swell reaches each plant on its ease-out: the struck point at once,
 * the radius at the swell's last tick, and nearer plants before farther ones
 * (decision reap-breeze-harvests-and-replants).
 */
class ReapStepTest {

    private static final double REACH = 4;
    private static final int SWELL = 10;

    @Test
    void theSwellReachesTheStruckPointAtOnceAndItsRadiusAtItsLastTick() {
        assertEquals(0, ReapStep.reachedAfter(0, REACH, SWELL));
        assertEquals(SWELL, ReapStep.reachedAfter(REACH, REACH, SWELL));
    }

    @Test
    void nearerPlantsAreReachedNoLaterThanFartherOnes() {
        long previous = -1;
        for (double distance = 0; distance <= REACH; distance += 0.25) {
            long reached = ReapStep.reachedAfter(distance, REACH, SWELL);
            assertTrue(reached >= previous, "the swell reached " + distance + " before a nearer plant");
            previous = reached;
        }
    }

    @Test
    void theSwellEasesOutReachingHalfItsRadiusEarly() {
        assertTrue(ReapStep.reachedAfter(REACH / 2, REACH, SWELL) < SWELL / 2);
    }
}
