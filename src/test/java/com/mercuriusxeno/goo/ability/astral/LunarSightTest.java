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
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Lunar looks for the moon in the overworld and for the blue planet in the moon's place in lunar, and finds neither
 * where the look strays, where the moon has set, or in any other dimension; the planet shows the moon's opposite
 * phase.
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
            assertEquals(Optional.of(LunarSight.Sky.MOON), LunarSight.skyAt(Level.OVERWORLD, NEARLY_UP, MIDNIGHT, LIT));
        }

        @Test
        void aLookWellOffTheMoonFindsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Level.OVERWORLD, WELL_OFF_UP, MIDNIGHT, LIT));
        }

        @Test
        void aMoonBelowTheHorizonIsNeverFound() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Level.OVERWORLD, DOWN, NOON, LIT));
        }

        @Test
        void theMoonIsFoundInTheOverworldAlone() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Level.NETHER, UP, MIDNIGHT, LIT));
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
        void aLookAlongTheMoonsPlaceInLunarFindsThePlanet() {
            assertEquals(Optional.of(LunarSight.Sky.BLUE_PLANET),
                    LunarSight.skyAt(Lunar.LEVEL, LunarSight.moonDirection(MOONSET / 2), MOONSET / 2, LIT));
        }

        @Test
        void aLookAtTheDiscsEdgeFindsThePlanet() {
            assertEquals(Optional.of(LunarSight.Sky.BLUE_PLANET),
                    LunarSight.skyAt(Lunar.LEVEL, offTheZenith(ON_THE_DISC_EDGE_DEGREES), MIDNIGHT, LIT));
        }

        @Test
        void aLookPastTheDiscsEdgeFindsNothing() {
            assertEquals(Optional.empty(),
                    LunarSight.skyAt(Lunar.LEVEL, offTheZenith(PAST_THE_DISC_DEGREES), MIDNIGHT, LIT));
        }

        /**
         * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
         */
        @Test
        void aLookAtThePlanetBelowTheHorizonFindsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, LunarSight.moonDirection(NOON), NOON, LIT));
        }

        /**
         * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
         */
        @Test
        void aLookAtTheDarkPlanetFindsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, UP, MIDNIGHT, MoonPhase.NEW_MOON));
        }

        @Test
        void aLookAtTheCrescentPlanetFindsIt() {
            assertEquals(Optional.of(LunarSight.Sky.BLUE_PLANET),
                    LunarSight.skyAt(Lunar.LEVEL, UP, MIDNIGHT, MoonPhase.WAXING_CRESCENT));
        }

        @Test
        void aLookAwayFromThePlanetInLunarFindsNothing() {
            assertEquals(Optional.empty(), LunarSight.skyAt(Lunar.LEVEL, DOWN, MIDNIGHT, LIT));
        }
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
                    LunarSight.refusalKey(dimension, LunarSight.moonDirection(MORNING), MORNING));
        }

        @ParameterizedTest
        @MethodSource("eachDimensionAndItsOtherRefusal")
        void aLookWellOffTheSunShowsTheOtherRefusal(ResourceKey<Level> dimension, String other) {
            assertEquals(other, LunarSight.refusalKey(dimension, DOWN, MORNING));
        }

        @ParameterizedTest
        @MethodSource("eachDimensionAndItsOtherRefusal")
        void aLookWhereTheSunStandsBelowTheHorizonShowsTheOtherRefusal(ResourceKey<Level> dimension, String other) {
            assertEquals(other, LunarSight.refusalKey(dimension, LunarSight.moonDirection(SUN_AT_MIDNIGHT),
                    SUN_AT_MIDNIGHT));
        }

        @Test
        void aLookUpInADimensionWithNoSunShowsTheRefusal() {
            assertEquals(LunarSight.LUNAR_REFUSED, LunarSight.refusalKey(Level.NETHER, UP, 0F));
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
