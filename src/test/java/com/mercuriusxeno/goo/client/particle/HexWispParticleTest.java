package com.mercuriusxeno.goo.client.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A streamed wisp is pulled in: it leaves the drained mob slowly and speeds
 * up as it nears the player, landing at the end of its flight
 * (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look).
 */
class HexWispParticleTest {

    private static final double TOLERANCE = 1e-9;
    private static final int SAMPLES = 20;

    @Test
    void aStreamedWispSpeedsUpAsItNearsThePlayer() {
        double previousStep = 0;
        for (int sample = 1; sample <= SAMPLES; sample++) {
            double step = HexWispParticle.pulledIn((double) sample / SAMPLES)
                    - HexWispParticle.pulledIn((double) (sample - 1) / SAMPLES);
            assertTrue(step > previousStep, "step " + sample);
            previousStep = step;
        }
    }

    @Test
    void aStreamedWispLeavesTheMobAndLandsOnThePlayer() {
        assertEquals(0, HexWispParticle.pulledIn(0), TOLERANCE);
        assertEquals(1, HexWispParticle.pulledIn(1), TOLERANCE);
    }
}
