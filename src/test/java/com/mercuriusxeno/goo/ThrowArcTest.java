package com.mercuriusxeno.goo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that blob flight time grows with the square root of distance scaled
 * by the type's levity plus its base flight time, per decision
 * flight-time-root-times-levity-plus-base, and that the arc peak grows with
 * the log of distance.
 */
class ThrowArcTest {

    private static final float DEFAULT_LEVITY = 1.0f;
    private static final float GLOW_LEVITY = 0.4f;
    private static final int BASE_FLIGHT_TIME = 3;
    private static final double LONG_THROW = 60;
    private static final double NEAR = 10;
    private static final double FAR = 40;
    private static final double HALF_BLOCK = 0.5;
    private static final double SIXTEEN_BLOCKS = 16;
    private static final double SIXTY_FOUR_BLOCKS = 64;
    private static final double PEAK_TOLERANCE = 1e-9;

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
     * The base peak reads 1 + 0.25 * log2(distance), floored at one block.
     */
    @Test
    void basePeakGrowsAQuarterBlockPerDoubling() {
        assertEquals(1.0, ThrowArc.basePeak(HALF_BLOCK), PEAK_TOLERANCE);
        assertEquals(1.0, ThrowArc.basePeak(1), PEAK_TOLERANCE);
        assertEquals(2.0, ThrowArc.basePeak(SIXTEEN_BLOCKS), PEAK_TOLERANCE);
        assertEquals(2.5, ThrowArc.basePeak(SIXTY_FOUR_BLOCKS), PEAK_TOLERANCE);
    }

    /**
     * The granny arc is the base peak times 1.15 plus one block.
     */
    @Test
    void grannyPeakScalesAndBoostsTheBase() {
        assertEquals(2.0 * 1.15 + 1.0, ThrowArc.grannyPeak(SIXTEEN_BLOCKS), PEAK_TOLERANCE);
    }
}
