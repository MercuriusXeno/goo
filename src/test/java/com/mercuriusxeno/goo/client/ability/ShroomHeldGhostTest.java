package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shroom's held cloud holds its motes within its ragged edge about the reach,
 * and turns them about the vertical alone (decision held-visual-ghosts-the-landing-in-two-passes).
 */
class ShroomHeldGhostTest {

    private static final double EPSILON = 1e-9;

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    @Test
    void everyMoteSitsWithinTheRaggedEdge() {
        double nearest = Double.MAX_VALUE;
        double farthest = 0;
        for (int i = 0; i < ShroomHeldGhost.MOTES; i++) {
            double reach = length(ShroomHeldGhost.moteAt(i, 0.7));
            nearest = Math.min(nearest, reach);
            farthest = Math.max(farthest, reach);
        }
        assertTrue(nearest >= 1 - ShroomHeldGhost.RAGGED_SHARE - EPSILON, String.valueOf(nearest));
        assertTrue(farthest <= 1 + ShroomHeldGhost.RAGGED_SHARE + EPSILON, String.valueOf(farthest));
        assertTrue(farthest - nearest > ShroomHeldGhost.RAGGED_SHARE, "the edge should run ragged, not smooth");
    }

    @Test
    void turningKeepsEachMotesHeightAndReach() {
        double[] still = ShroomHeldGhost.moteAt(17, 0);
        double[] turned = ShroomHeldGhost.moteAt(17, 1.3);
        assertEquals(still[1], turned[1], EPSILON);
        assertEquals(length(still), length(turned), EPSILON);
    }

}
