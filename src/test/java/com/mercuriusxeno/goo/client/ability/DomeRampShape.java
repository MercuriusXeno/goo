package com.mercuriusxeno.goo.client.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the shape every dome's fuse-tail ramp takes (decision
 * dome-fades-in-before-its-start): near nothing and transparent at its
 * start, rising without a dip, and meeting the burnout's first drawn frame
 * at full opacity.
 */
final class DomeRampShape {

    private static final float TOLERANCE = 1e-5f;
    private static final float NEAR_NOTHING = 0.1f;
    private static final int SAMPLES = 24;
    private static final int FULL_ALPHA = 0xFF;

    /** A value the dome draws at a ramp share. */
    @FunctionalInterface
    interface RampCurve {
        /**
         * @param ramp the ramp's share in [0, 1]
         * @return the value at that share
         */
        float at(float ramp);
    }

    private DomeRampShape() {
    }

    /**
     * @param rampRadius       the dome's radius at a ramp share
     * @param firstFrameRadius the radius the visual's own radius function gives at its first drawn tick
     * @param alphaOf          the alpha byte the dome packs at a ramp share
     */
    static void assertRampMeetsFirstFrame(RampCurve rampRadius, float firstFrameRadius,
                                          RampCurve alphaOf) {
        assertTrue(firstFrameRadius > 0f, "the first drawn frame has no radius to meet");
        assertTrue(rampRadius.at(0f) < NEAR_NOTHING * firstFrameRadius, "the ramp starts too big");
        assertEquals(0f, alphaOf.at(0f), 0f, "the ramp starts visible");
        assertEquals(firstFrameRadius, rampRadius.at(1f), TOLERANCE, "the ramp misses the first frame's radius");
        assertEquals(FULL_ALPHA, alphaOf.at(1f), 0f, "the ramp ends short of full opacity");
        float previousRadius = rampRadius.at(0f);
        float previousAlpha = alphaOf.at(0f);
        for (int i = 1; i <= SAMPLES; i++) {
            float ramp = i / (float) SAMPLES;
            float radius = rampRadius.at(ramp);
            float alpha = alphaOf.at(ramp);
            assertTrue(radius > previousRadius, "the radius stalls or dips at ramp " + ramp);
            assertTrue(alpha >= previousAlpha, "the opacity dips at ramp " + ramp);
            previousRadius = radius;
            previousAlpha = alpha;
        }
    }
}
