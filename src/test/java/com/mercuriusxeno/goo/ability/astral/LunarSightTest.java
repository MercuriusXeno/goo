package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar looks for the moon in the overworld and for the blue planet in lunar, hanging fixed where the caster's x stands
 * it, and finds neither where the look strays, where the moon has set, on the dark side, or in any other dimension;
 * the planet shows the moon's opposite phase.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 */
class LunarSightTest {

    private static final double TOLERANCE = 1e-6;
    /** The moon at midnight stands straight overhead. */
    private static final float MIDNIGHT = 0F;
    /** A quarter turn on, the moon stands on the western horizon; vanilla's sky turns it toward negative x. */
    private static final float MOONSET = 90F;
    /** Half a turn on, the moon stands straight below. */
    private static final float NOON = 180F;
    /** A phase lighting the planet's face. */
    private static final MoonPhase LIT = MoonPhase.FULL_MOON;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 DOWN = new Vec3(0, -1, 0);
    /** A look a few degrees off straight up, inside Lunar's reach. */
    private static final Vec3 NEARLY_UP = new Vec3(0.05, 1, 0).normalize();
    /** A look well off straight up, outside it. */
    private static final Vec3 WELL_OFF_UP = new Vec3(0.5, 1, 0).normalize();
    /** An x on lunar's light side, where the sun and the planet keep the overworld's clock. */
    private static final double LIGHT_SIDE_X = 0;
    /** An x on lunar's dark side, past 3.75 million. */
    private static final double DARK_SIDE_X = 5_000_000;
    /** The degrees a full turn of the moon angle spans, one whole day and night. */
    private static final int FULL_CYCLE_DEGREES = 360;
    private static final int DAY_TICKS = 24_000;
    private static final int NOON_TICK = 6000;
    /** Half a day on from noon, when the overworld's moon stands overhead where at noon it stood underfoot. */
    private static final int MIDNIGHT_TICK = 18000;
    /** A light-side x off x 0, where the planet stands well off the zenith. */
    private static final double PLANET_X = 1_000_000;
    /** A sun angle well clear of the planet at x 0 and at PLANET_X, so no eclipse shows it. */
    private static final float SUN_CLEAR = 90F;

    @Nested
    class TheMoon {

        @Test
        void standsOverheadAtMidnight() {
            Vec3 moon = LunarSight.moonDirection(MIDNIGHT);
            assertEquals(0.0, moon.x(), TOLERANCE);
            assertEquals(1.0, moon.y(), TOLERANCE);
            assertEquals(0.0, moon.z(), TOLERANCE);
        }

        @Test
        void turnsTowardNegativeXAsItSets() {
            Vec3 moon = LunarSight.moonDirection(MOONSET);
            assertEquals(-1.0, moon.x(), TOLERANCE);
            assertEquals(0.0, moon.y(), TOLERANCE);
        }

        @Test
        void aLookAtTheMoonInTheOverworldFindsIt() {
            assertEquals(Optional.of(LunarSight.Sky.MOON),
                    LunarSight.skyAt(Level.OVERWORLD, LIGHT_SIDE_X, NEARLY_UP, MIDNIGHT, SUN_CLEAR, LIT));
        }

        @Test
        void aLookWellOffTheMoonFindsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Level.OVERWORLD, LIGHT_SIDE_X, WELL_OFF_UP, MIDNIGHT, SUN_CLEAR, LIT));
        }

        @Test
        void aMoonBelowTheHorizonIsNeverFound() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Level.OVERWORLD, LIGHT_SIDE_X, DOWN, NOON, SUN_CLEAR, LIT));
        }

        @Test
        void theMoonIsFoundInTheOverworldAlone() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Level.NETHER, LIGHT_SIDE_X, UP, MIDNIGHT, SUN_CLEAR, LIT));
        }

        /**
         * decision moon-trip-refuses-under-the-new-moon
         */
        @ParameterizedTest
        @EnumSource(value = MoonPhase.class, names = "NEW_MOON", mode = EnumSource.Mode.EXCLUDE)
        void aLookAtALitMoonInTheOverworldFindsIt(MoonPhase moonPhase) {
            assertEquals(Optional.of(LunarSight.Sky.MOON),
                    LunarSight.skyAt(Level.OVERWORLD, LIGHT_SIDE_X, NEARLY_UP, MIDNIGHT, SUN_CLEAR, moonPhase));
        }

        /**
         * decision moon-trip-refuses-under-the-new-moon
         */
        @Test
        void aLookAtTheNewMoonInTheOverworldFindsNothing() {
            assertEquals(Optional.empty(),
                    LunarSight.skyAt(Level.OVERWORLD, LIGHT_SIDE_X, NEARLY_UP, MIDNIGHT, SUN_CLEAR, MoonPhase.NEW_MOON));
        }
    }

    /**
     * decision blue-star-takes-the-moons-place-with-the-opposite-phase
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     */
    @Nested
    class TheBluePlanet {

        /** Inside the planet's disc, past the look reach a smaller body holds: its half width is near 11.3 degrees. */
        private static final double ON_THE_DISC_EDGE_DEGREES = 11.0;
        /** Just past the disc's edge. */
        private static final double PAST_THE_DISC_DEGREES = 12.0;

        private Vec3 offTheZenith(double degrees) {
            double angle = Math.toRadians(degrees);
            return new Vec3(0, Math.cos(angle), Math.sin(angle));
        }

        @Test
        void aLookAtTheDiscsEdgeFindsThePlanet() {
            assertEquals(Optional.of(LunarSight.Sky.BLUE_PLANET),
                    LunarSight.skyAt(Lunar.LEVEL, LIGHT_SIDE_X, offTheZenith(ON_THE_DISC_EDGE_DEGREES), MIDNIGHT, SUN_CLEAR, LIT));
        }

        @Test
        void aLookPastTheDiscsEdgeFindsNothing() {
            assertEquals(Optional.empty(),
                    LunarSight.skyAt(Lunar.LEVEL, LIGHT_SIDE_X, offTheZenith(PAST_THE_DISC_DEGREES), MIDNIGHT, SUN_CLEAR, LIT));
        }

        /**
         * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
         */
        @Test
        void aLookAtTheDarkPlanetFindsNothing() {
            assertEquals(Optional.empty(),
                    LunarSight.skyAt(Lunar.LEVEL, LIGHT_SIDE_X, UP, MIDNIGHT, SUN_CLEAR, MoonPhase.NEW_MOON));
        }

        @Test
        void aLookAtTheCrescentPlanetFindsIt() {
            assertEquals(Optional.of(LunarSight.Sky.BLUE_PLANET),
                    LunarSight.skyAt(Lunar.LEVEL, LIGHT_SIDE_X, UP, MIDNIGHT, SUN_CLEAR, MoonPhase.WAXING_CRESCENT));
        }

        /**
         * decision dark-side-is-the-half-of-the-moon-past-the-light-side
         */
        @ParameterizedTest
        @ValueSource(doubles = {3_750_001, -3_750_001, 5_000_000, -6_000_000, 7_500_000})
        void theDarkSideNeverShowsThePlanet(double x) {
            assertFalse(LunarSight.planetShows(x, LIT, SUN_CLEAR));
        }

        /**
         * decision dark-side-is-the-half-of-the-moon-past-the-light-side
         */
        @ParameterizedTest
        @ValueSource(doubles = {0, 1_000_000, -2_000_000, 3_749_999, -3_749_999})
        void theLightSideShowsThePlanetOnALitPhase(double x) {
            assertTrue(LunarSight.planetShows(x, LIT, SUN_CLEAR));
        }

        /**
         * decision dark-side-is-the-half-of-the-moon-past-the-light-side
         */
        @Test
        void aLookAtThePlanetsPlaceOnTheDarkSideFindsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, DARK_SIDE_X, UP, MIDNIGHT, SUN_CLEAR, LIT));
        }

        @Test
        void aLookAwayFromThePlanetInLunarFindsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, LIGHT_SIDE_X, DOWN, MIDNIGHT, SUN_CLEAR, LIT));
        }
    }

    /**
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     */
    @Nested
    class ThePlanetsFixedDirection {

        /** Half the light side out from x 0, where the planet stands halfway down to the horizon. */
        private static final double HALFWAY_OUT = 1_875_000;
        private static final double HALFWAY_HEIGHT = Math.sqrt(0.5);
        /** Two thirds of the way from x 0 to the seam, a third of a turn round from the planet. */
        private static final double FAR_ON_THE_DARK_SIDE = 5_000_000;
        private static final double FAR_HEIGHT = -0.5;

        private static Stream<Arguments> eachXAndThePlanetsHeight() {
            return Stream.of(Arguments.of(0.0, 1.0),
                    Arguments.of(HALFWAY_OUT, HALFWAY_HEIGHT), Arguments.of(-HALFWAY_OUT, HALFWAY_HEIGHT),
                    Arguments.of((double) Lunar.LIGHT_SIDE_HALF_WIDTH, 0.0),
                    Arguments.of((double) -Lunar.LIGHT_SIDE_HALF_WIDTH, 0.0),
                    Arguments.of(FAR_ON_THE_DARK_SIDE, FAR_HEIGHT), Arguments.of(-FAR_ON_THE_DARK_SIDE, FAR_HEIGHT));
        }

        @ParameterizedTest
        @MethodSource("eachXAndThePlanetsHeight")
        void thePlanetsHeightIsSetByX(double x, double height) {
            Vec3 planet = LunarSight.planetDirection(x);
            assertEquals(height, planet.y(), TOLERANCE, "at x " + x);
            assertEquals(1.0, planet.length(), TOLERANCE, "at x " + x);
        }

        @Test
        void thePlanetLeansBackTowardXZero() {
            assertTrue(LunarSight.planetDirection(HALFWAY_OUT).x() < 0.0);
            assertTrue(LunarSight.planetDirection(-HALFWAY_OUT).x() > 0.0);
        }

        @Test
        void thePlanetStandsInTheSameDirectionAtTwoClockTimesHalfADayApart() {
            assertEquals(LunarSight.skyDirection(Lunar.LEVEL, PLANET_X, overworldMoonAngleAt(NOON_TICK)),
                    LunarSight.skyDirection(Lunar.LEVEL, PLANET_X, overworldMoonAngleAt(MIDNIGHT_TICK)));
        }

        @Test
        void theOverworldsMoonStillTurnsWithTheClock() {
            assertNotEquals(LunarSight.skyDirection(Level.OVERWORLD, PLANET_X, overworldMoonAngleAt(NOON_TICK)),
                    LunarSight.skyDirection(Level.OVERWORLD, PLANET_X, overworldMoonAngleAt(MIDNIGHT_TICK)));
        }
    }

    /**
     * decision blue-planet-hangs-fixed-with-its-height-set-by-x
     * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
     */
    @Nested
    class TheReturnAtEveryHour {

        private static final double FAR_ON_THE_DARK_SIDE = 4_000_000;

        @ParameterizedTest
        @ValueSource(ints = {0, 6000, 12000, 18000})
        void aLookAtThePlanetsFixedDirectionHoldsItAtEveryHourOfALitPhase(int dayTick) {
            assertEquals(Optional.of(LunarSight.Sky.BLUE_PLANET), LunarSight.skyAt(Lunar.LEVEL, PLANET_X,
                    LunarSight.planetDirection(PLANET_X), overworldMoonAngleAt(dayTick), SUN_CLEAR, LIT));
        }

        /**
         * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
         */
        @ParameterizedTest
        @ValueSource(floats = {0F, 8F, -8F})
        void aLookAtTheNewPlanetHoldsItWhileItEclipsesTheSun(float sunOffTheCenter) {
            float sun = (float) Lunar.planetAngleDegrees(PLANET_X) + sunOffTheCenter;
            assertEquals(Optional.of(LunarSight.Sky.BLUE_PLANET), LunarSight.skyAt(Lunar.LEVEL, PLANET_X,
                    LunarSight.planetDirection(PLANET_X), MIDNIGHT, sun, MoonPhase.NEW_MOON));
        }

        /**
         * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
         */
        @ParameterizedTest
        @ValueSource(floats = {15F, -15F, 22.5F, 90F})
        void aLookAtTheNewPlanetHoldsNothingWithTheSunWellClearOfIt(float sunOffTheCenter) {
            float sun = (float) Lunar.planetAngleDegrees(PLANET_X) + sunOffTheCenter;
            assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, PLANET_X,
                    LunarSight.planetDirection(PLANET_X), MIDNIGHT, sun, MoonPhase.NEW_MOON));
        }

        @Test
        void aLookStraightUpAwayFromThePlanetsFixedDirectionHoldsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, PLANET_X, UP, MIDNIGHT, SUN_CLEAR, LIT));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 6000, 12000, 18000})
        void anyLookOnTheDarkSideHoldsNothing(int dayTick) {
            float moonAngle = overworldMoonAngleAt(dayTick);
            for (Vec3 look : new Vec3[] {LunarSight.planetDirection(FAR_ON_THE_DARK_SIDE), UP, DOWN,
                LunarSight.moonDirection(moonAngle)}) {
                assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, FAR_ON_THE_DARK_SIDE, look, moonAngle,
                        (float) Lunar.planetAngleDegrees(FAR_ON_THE_DARK_SIDE), LIT), "look " + look + " at day tick " + dayTick);
            }
        }
    }

    /**
     * The overworld's moon angle near a day tick, on vanilla's turn without its easing: half a turn at noon's tick
     * 6000, a full turn a day.
     *
     * @param dayTick the day tick
     * @return the moon angle, in degrees
     */
    private static float overworldMoonAngleAt(int dayTick) {
        return NOON + (dayTick - NOON_TICK) * FULL_CYCLE_DEGREES / (float) DAY_TICKS;
    }

    /**
     * decision lunar-refusal-says-thats-no-moon
     * decision terran-refusal-says-thats-not-your-world
     */
    @Nested
    class TheRefusal {

        @Test
        void onTheMoonTerranRefusesWithThatsNotYourWorld() {
            assertEquals(LunarSight.TERRAN_REFUSED, LunarSight.refusalKey(Lunar.LEVEL));
        }

        @Test
        void inTheOverworldLunarRefusesWithThatsNoMoon() {
            assertEquals(LunarSight.LUNAR_REFUSED, LunarSight.refusalKey(Level.OVERWORLD));
        }

        @Test
        void theTwoRefusalsReadApart() {
            assertNotEquals(LunarSight.LUNAR_REFUSED, LunarSight.TERRAN_REFUSED);
        }
    }

    /**
     * decision pointing-at-the-sun-says-the-big-yellow-one-is-the-sun
     */
    @Nested
    class TheSunJoke {

        /** A morning sun angle, the sun well up in the east. */
        private static final float MORNING = -40F;
        /** The midnight sun angle, the sun straight underfoot. */
        private static final float SUN_AT_MIDNIGHT = 180F;

        private static Stream<Arguments> eachDimensionAndItsOtherRefusal() {
            return Stream.of(Arguments.of(Level.OVERWORLD, LunarSight.LUNAR_REFUSED),
                    Arguments.of(Lunar.LEVEL, LunarSight.TERRAN_REFUSED));
        }

        @ParameterizedTest
        @MethodSource("eachDimensionAndItsOtherRefusal")
        void aLookAtTheSunByDaySaysTheBigYellowOneIsTheSun(ResourceKey<Level> dimension, String other) {
            assertEquals(LunarSight.SUN_REFUSED,
                    LunarSight.refusalKey(dimension, LIGHT_SIDE_X, LunarSight.moonDirection(MORNING), MORNING));
        }

        @ParameterizedTest
        @MethodSource("eachDimensionAndItsOtherRefusal")
        void aLookWellOffTheSunShowsTheOtherRefusal(ResourceKey<Level> dimension, String other) {
            assertEquals(other, LunarSight.refusalKey(dimension, LIGHT_SIDE_X, DOWN, MORNING));
        }

        @ParameterizedTest
        @MethodSource("eachDimensionAndItsOtherRefusal")
        void aLookWhereTheSunStandsBelowTheHorizonShowsTheOtherRefusal(ResourceKey<Level> dimension, String other) {
            assertEquals(other, LunarSight.refusalKey(dimension, LIGHT_SIDE_X, LunarSight.moonDirection(SUN_AT_MIDNIGHT),
                    SUN_AT_MIDNIGHT));
        }

        /**
         * Through the planet's dark phase the sun stands behind it, and the planet's disc, four moons wide, hides it.
         * decision sun-follows-the-phase-and-lights-the-far-side-too
         * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
         */
        @Test
        void aLookAtTheSunBehindThePlanetShowsTerransRefusal() {
            Vec3 planet = LunarSight.planetDirection(LIGHT_SIDE_X);
            assertEquals(LunarSight.TERRAN_REFUSED, LunarSight.refusalKey(Lunar.LEVEL, LIGHT_SIDE_X, planet, 0F));
        }

        @Test
        void aLookAtTheSunClearOfThePlanetOnTheDarkSideSaysTheBigYellowOneIsTheSun() {
            assertEquals(LunarSight.SUN_REFUSED, LunarSight.refusalKey(Lunar.LEVEL, DARK_SIDE_X, UP, 0F));
        }

        @Test
        void aLookUpInADimensionWithNoSunShowsTheRefusal() {
            assertEquals(LunarSight.LUNAR_REFUSED, LunarSight.refusalKey(Level.NETHER, LIGHT_SIDE_X, UP, 0F));
        }
    }

    /**
     * decision blue-star-takes-the-moons-place-with-the-opposite-phase
     */
    @Nested
    class ThePlanetsPhase {

        private static Stream<Arguments> eachMoonPhaseAndItsOpposite() {
            return Stream.of(
                    Arguments.of(MoonPhase.NEW_MOON, MoonPhase.FULL_MOON),
                    Arguments.of(MoonPhase.FULL_MOON, MoonPhase.NEW_MOON),
                    Arguments.of(MoonPhase.WANING_GIBBOUS, MoonPhase.WAXING_CRESCENT),
                    Arguments.of(MoonPhase.THIRD_QUARTER, MoonPhase.FIRST_QUARTER),
                    Arguments.of(MoonPhase.WANING_CRESCENT, MoonPhase.WAXING_GIBBOUS),
                    Arguments.of(MoonPhase.WAXING_CRESCENT, MoonPhase.WANING_GIBBOUS),
                    Arguments.of(MoonPhase.FIRST_QUARTER, MoonPhase.THIRD_QUARTER),
                    Arguments.of(MoonPhase.WAXING_GIBBOUS, MoonPhase.WANING_CRESCENT));
        }

        @ParameterizedTest
        @MethodSource("eachMoonPhaseAndItsOpposite")
        void thePlanetShowsTheMoonsOppositePhase(MoonPhase moon, MoonPhase planet) {
            assertEquals(planet, LunarSight.planetPhase(moon));
        }
    }
}
