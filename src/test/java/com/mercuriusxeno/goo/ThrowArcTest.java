package com.mercuriusxeno.goo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that blob flight time grows with the square root of distance scaled
 * by the type's levity plus its base flight time, per decision
 * flight-time-root-times-levity-plus-base, and that the arc it feeds stays flat.
 */
class ThrowArcTest {

    private static final float DEFAULT_LEVITY = 1.0f;
    private static final float GLOW_LEVITY = 0.4f;
    private static final int BASE_FLIGHT_TIME = 3;
    private static final double LONG_THROW = 60;
    private static final double NEAR = 10;
    private static final double FAR = 40;

    /**
     * A 60-block throw at the default levity and base lands in 11 ticks.
     */
    @Test
    void longThrowAtDefaultLevityTakesElevenTicks() {
        assertEquals(11, ThrowArc.travelTicks(LONG_THROW, DEFAULT_LEVITY, BASE_FLIGHT_TIME));
    }

    /**
     * A zero-distance throw takes exactly the base flight time.
     */
    @Test
    void zeroDistanceTakesTheBase() {
        assertEquals(BASE_FLIGHT_TIME, ThrowArc.travelTicks(0, DEFAULT_LEVITY, BASE_FLIGHT_TIME));
    }

    /**
     * Glow's lower levity lands a 60-block throw in 7 ticks.
     */
    @Test
    void glowLevityFliesFaster() {
        assertEquals(7, ThrowArc.travelTicks(LONG_THROW, GLOW_LEVITY, BASE_FLIGHT_TIME));
    }

    /**
     * Quadrupling the distance grows the ticks by less than four times.
     */
    @Test
    void ticksGrowSlowerThanDistance() {
        double near = ThrowArc.travelTicks(NEAR, DEFAULT_LEVITY, BASE_FLIGHT_TIME);
        double far = ThrowArc.travelTicks(FAR, DEFAULT_LEVITY, BASE_FLIGHT_TIME);
        assertTrue(far > near);
        assertTrue(far / near < FAR / NEAR);
    }

    /**
     * A 60-block throw at the default levity peaks under one block on the base arc.
     */
    @Test
    void longThrowPeaksUnderOneBlock() {
        assertTrue(ThrowArc.basePeak(ThrowArc.travelTicks(LONG_THROW, DEFAULT_LEVITY, BASE_FLIGHT_TIME)) < 1.0);
    }
}
