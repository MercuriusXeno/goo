package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bore's vortex is one block wide along its whole length, from the eye out
 * to Bore's reach, matching the line Bore drills
 * (decision bore-breaks-a-15-degree-cone).
 */
class BoreVortexTest {

    /** rock_bore.json's delivery range. */
    private static final double REACH = 4;
    private static final double HALF_A_BLOCK = 0.5;
    private static final int SAMPLES = 16;
    private static final double SLACK = 1e-9;

    @Test
    void theRadiusIsHalfABlockAtEverySampledDistanceOutToTheReach() {
        for (int sample = 0; sample <= SAMPLES; sample++) {
            double distance = REACH * sample / SAMPLES;
            assertEquals(HALF_A_BLOCK, BoreVortex.radiusAt(distance), SLACK, "at " + distance);
        }
    }
}
