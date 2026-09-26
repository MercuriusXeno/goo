package com.mercuriusxeno.goo;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that blob flight time grows with the square root of distance scaled
 * by the type's levity plus its base flight time, per decision
 * flight-time-root-times-levity-plus-base, and that the arc peak grows as
 * 0.2 times distance to the power 0.75.
 */
class ThrowArcTest {

    private static final float DEFAULT_LEVITY = 1.0f;
    private static final float GLOW_LEVITY = 0.4f;
    private static final int BASE_FLIGHT_TIME = 3;
    private static final double LONG_THROW = 60;
    private static final double NEAR = 10;
    private static final double FAR = 40;
    private static final double ONE_BLOCK = 1;
    private static final double SIXTEEN_BLOCKS = 16;
    private static final double EIGHTY_ONE_BLOCKS = 81;
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
     * The base peak reads 0.2 times distance to the power 0.75.
     */
    @Test
    void basePeakIsScaledPowerOfDistance() {
        assertEquals(0.0, ThrowArc.basePeak(0), PEAK_TOLERANCE);
        assertEquals(0.2, ThrowArc.basePeak(ONE_BLOCK), PEAK_TOLERANCE);
        assertEquals(1.6, ThrowArc.basePeak(SIXTEEN_BLOCKS), PEAK_TOLERANCE);
        assertEquals(5.4, ThrowArc.basePeak(EIGHTY_ONE_BLOCKS), PEAK_TOLERANCE);
    }

    /**
     * The granny arc is the base peak times 1.15 plus one block.
     */
    @Test
    void grannyPeakScalesAndBoostsTheBase() {
        assertEquals(1.6 * 1.15 + 1.0, ThrowArc.grannyPeak(SIXTEEN_BLOCKS), PEAK_TOLERANCE);
    }

    /**
     * An aim line origin within reach of the eye is the flight's start
     * unchanged, whether the player looks up, level or down.
     */
    @Test
    void originWithinReachPassesThrough() {
        Vec3 eye = new Vec3(10, 65, -4);
        Vec3 lookingUp = eye.add(0.4, 0.2, 0.6);
        Vec3 level = eye.add(0.4, -0.3, 0.6);
        Vec3 lookingDown = eye.add(0.4, -0.7, 0.3);
        assertEquals(lookingUp, ThrowArc.clampToReach(eye, lookingUp, ThrowArc.HAND_REACH));
        assertEquals(level, ThrowArc.clampToReach(eye, level, ThrowArc.HAND_REACH));
        assertEquals(lookingDown, ThrowArc.clampToReach(eye, lookingDown, ThrowArc.HAND_REACH));
    }

    /**
     * An origin beyond reach is pulled onto the reach sphere around the eye,
     * along the line from the eye to the origin.
     */
    @Test
    void originBeyondReachIsPulledOntoTheSphere() {
        Vec3 eye = new Vec3(10, 65, -4);
        Vec3 forged = eye.add(0, 0, 30);
        Vec3 clamped = ThrowArc.clampToReach(eye, forged, ThrowArc.HAND_REACH);
        assertEquals(ThrowArc.HAND_REACH, clamped.distanceTo(eye), PEAK_TOLERANCE);
        assertEquals(eye.add(0, 0, ThrowArc.HAND_REACH).distanceTo(clamped), 0.0, PEAK_TOLERANCE);
    }
}
