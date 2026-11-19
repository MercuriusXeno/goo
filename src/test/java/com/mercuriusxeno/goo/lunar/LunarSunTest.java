package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lunar's sun follows the phase cycle: it stands where the planet stands at every x at the overworld's midnight under
 * its full moon, opposite it half a cycle on, one eighth of a turn round each overworld day, back where it began after
 * the whole cycle of 192,000 ticks.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
class LunarSunTest {

    private static final double TOLERANCE = 1e-6;
    /** The overworld's midnight under its first full moon: the planet stands dark, the sun behind it. */
    private static final long FULL_MOON_MIDNIGHT = 18_000;
    private static final long HALF_A_CYCLE = 96_000;
    private static final long OVERWORLD_DAY = 24_000;
    private static final long WHOLE_CYCLE = 192_000;
    private static final double HALF_TURN = 180.0;
    private static final double EIGHTH_TURN = 45.0;
    private static final double QUARTER_TURN = 90.0;

    @ParameterizedTest
    @ValueSource(doubles = {0, 2_000_000, -6_000_000})
    void theSunStandsWhereThePlanetStandsAtTheFullMoonsMidnight(double x) {
        assertSameAngle(Lunar.planetAngleDegrees(x), LunarSun.sunAngleDegrees(x, FULL_MOON_MIDNIGHT));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 2_000_000, -6_000_000})
    void theSunStandsOppositeThePlanetHalfACycleOn(double x) {
        assertSameAngle(Lunar.planetAngleDegrees(x) + HALF_TURN,
                LunarSun.sunAngleDegrees(x, FULL_MOON_MIDNIGHT + HALF_A_CYCLE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 2_000_000, -6_000_000})
    void theSunTurnsAnEighthOfACircleEachOverworldDay(double x) {
        assertSameAngle(LunarSun.sunAngleDegrees(x, FULL_MOON_MIDNIGHT) + EIGHTH_TURN,
                LunarSun.sunAngleDegrees(x, FULL_MOON_MIDNIGHT + OVERWORLD_DAY));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 18_000, 57_321, 150_000})
    void theSunReturnsAfterTheWholeCycle(long clockTime) {
        assertSameAngle(LunarSun.sunAngleDegrees(2_000_000, clockTime),
                LunarSun.sunAngleDegrees(2_000_000, clockTime + WHOLE_CYCLE));
    }

    /**
     * The angle grows with the clock, and a growing angle carries a body from overhead toward the western horizon, so
     * the sun rises in the east and sets in the west.
     */
    @Test
    void theSunSetsInTheWestAQuarterCycleOnFromOverhead() {
        assertSameAngle(QUARTER_TURN, LunarSun.sunAngleDegrees(0, FULL_MOON_MIDNIGHT + WHOLE_CYCLE / 4));
    }

    private static void assertSameAngle(double expected, double actual) {
        double apart = Math.floorMod(Math.round((actual - expected) * 1e6), Math.round(360 * 1e6)) / 1e6;
        assertEquals(0.0, Math.min(apart, 360 - apart), TOLERANCE, "expected " + expected + " but was " + actual);
    }
}
