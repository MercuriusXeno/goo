package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's day and night at an x follow the sun's height there, on both halves of the moon alike: day's light where the
 * sun stands high, midnight's where it stands underfoot, lunar_day's twilight ramp between, and no step at the line
 * between the halves.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 * decision dark-side-is-the-half-of-the-moon-past-the-light-side
 */
class LunarNightTest {

    /** The sky darkening of full day: lunar_day's sky light level of 15, darkened by nothing. */
    private static final int DAY_DARKEN = 0;
    /** The sky darkening of midnight: 15 less lunar_day's night sky light level of 4. */
    private static final int MIDNIGHT_DARKEN = 11;
    private static final float DAY_SKY_LIGHT_FACTOR = 1F;
    private static final float NIGHT_SKY_LIGHT_FACTOR = 0.24F;
    private static final float FACTOR_TOLERANCE = 1e-6F;
    private static final double OVERHEAD = 0;
    private static final double SUNSET = 90;
    private static final double UNDERFOOT = 180;
    private static final long WHOLE_CYCLE = 192_000;
    private static final long FULL_MOON_MIDNIGHT = 18_000;
    private static final int CLOCK_SAMPLES = 16;
    private static final double JUST_INSIDE_THE_LINE = 3_749_999;
    private static final double JUST_PAST_THE_LINE = 3_750_001;
    /** Where the open-sky probe stands above the ground, whose height the light reads leave unread. */
    private static final double PROBE_Y = 200;

    /**
     * The overworld clock time the sun stands at an angle over an x, in the first phase cycle.
     *
     * @param x            the x, in blocks
     * @param angleDegrees the sun angle, in degrees
     * @return the overworld clock time
     */
    static long clockWhenTheSunStandsAt(double x, double angleDegrees) {
        double turn = Math.floorMod(Math.round((angleDegrees - Lunar.planetAngleDegrees(x)) * 1e6), 360_000_000L) / 1e6;
        return FULL_MOON_MIDNIGHT + Math.round(turn / 360 * WHOLE_CYCLE);
    }

    @Nested
    class TheSkysDarkening {

        @ParameterizedTest
        @ValueSource(doubles = {1_000_000, -2_500_000, 5_000_000, -6_500_000})
        void theSkyReadsDayWithTheSunOverhead(double x) {
            assertEquals(DAY_DARKEN, LunarNight.skyDarkenAt(x, clockWhenTheSunStandsAt(x, OVERHEAD)));
        }

        @ParameterizedTest
        @ValueSource(doubles = {1_000_000, -2_500_000, 5_000_000, -6_500_000})
        void theSkyReadsMidnightWithTheSunUnderfoot(double x) {
            assertEquals(MIDNIGHT_DARKEN, LunarNight.skyDarkenAt(x, clockWhenTheSunStandsAt(x, UNDERFOOT)));
        }

        @ParameterizedTest
        @ValueSource(doubles = {1_000_000, 5_000_000})
        void theSkyReadsTwilightWithTheSunOnTheHorizon(double x) {
            int darken = LunarNight.skyDarkenAt(x, clockWhenTheSunStandsAt(x, SUNSET));
            assertTrue(darken > DAY_DARKEN && darken < MIDNIGHT_DARKEN, "darken at sunset " + darken);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
        void theSkyDarkensAlikeEitherSideOfTheLine(int sample) {
            long clockTime = sample * WHOLE_CYCLE / CLOCK_SAMPLES;
            assertEquals(LunarNight.skyDarkenAt(JUST_INSIDE_THE_LINE, clockTime),
                    LunarNight.skyDarkenAt(JUST_PAST_THE_LINE, clockTime), "at clock time " + clockTime);
            assertEquals(LunarNight.skyDarkenAt(-JUST_INSIDE_THE_LINE, clockTime),
                    LunarNight.skyDarkenAt(-JUST_PAST_THE_LINE, clockTime), "at clock time " + clockTime);
        }
    }

    /**
     * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
     */
    @Nested
    class TheSkyLightFactorLayer {

        @ParameterizedTest
        @ValueSource(doubles = {5_000_000, -6_500_000})
        void theDarkSideReadsDaysFactorWithTheSunOverhead(double x) {
            assertEquals(DAY_SKY_LIGHT_FACTOR, factorAt(x, OVERHEAD), FACTOR_TOLERANCE);
        }

        @ParameterizedTest
        @ValueSource(doubles = {5_000_000, -6_500_000})
        void theDarkSideReadsNightsFactorWithTheSunUnderfoot(double x) {
            assertEquals(NIGHT_SKY_LIGHT_FACTOR, factorAt(x, UNDERFOOT), FACTOR_TOLERANCE);
        }

        @ParameterizedTest
        @ValueSource(doubles = {1_000_000, -2_500_000})
        void theLightSideReadsNightsFactorWithTheSunUnderfoot(double x) {
            assertEquals(NIGHT_SKY_LIGHT_FACTOR, factorAt(x, UNDERFOOT), FACTOR_TOLERANCE);
        }

        private float factorAt(double x, double sunAngleDegrees) {
            long clockTime = clockWhenTheSunStandsAt(x, sunAngleDegrees);
            return LunarNight.skyLightFactorLayer(() -> clockTime)
                    .applyPositional(DAY_SKY_LIGHT_FACTOR, new Vec3(x, PROBE_Y, 0), null);
        }
    }

    /**
     * decision sun-follows-the-phase-and-lights-the-far-side-too
     */
    @Nested
    class TheSunAngleLayer {

        private static final float ANGLE_TOLERANCE = 1e-2F;
        /** The sun angle lunar_day's timeline would answer, which the layer answers in place of. */
        private static final float TIMELINES_ANGLE = 123F;

        @ParameterizedTest
        @ValueSource(doubles = {1_000_000, 5_000_000, -6_500_000})
        void theSunAngleIsTheSunsAtTheProbesX(double x) {
            long clockTime = clockWhenTheSunStandsAt(x, SUNSET);
            float angle = LunarNight.sunAngleLayer(() -> clockTime)
                    .applyPositional(TIMELINES_ANGLE, new Vec3(x, PROBE_Y, 0), null);
            assertEquals((float) SUNSET, angle, ANGLE_TOLERANCE);
        }
    }
}
