package com.mercuriusxeno.goo.lunar;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lunar's gravity: one sixth of the overworld's in lunar and the overworld's everywhere else, and a thrown goo blob's
 * scripted flight stretched in lunar to the time one-sixth gravity takes to draw the same arc.
 * decision lunar-gravity-is-one-sixth-of-the-overworlds
 */
class LunarGravityTest {

    private static final double EXACT = 1e-9;
    private static final double ONE_SIXTH = 1.0 / 6.0;
    private static final int OVERWORLD_FLIGHT_TICKS = 20;
    /** One-sixth gravity draws the same peak over the square root of six times the ticks: 20 times 2.449, rounded. */
    private static final int LUNAR_FLIGHT_TICKS = 49;

    static Stream<ResourceKey<Level>> everyOtherDimension() {
        return Stream.of(Level.OVERWORLD, Level.NETHER, Level.END);
    }

    @Nested
    class Scale {
        @Test
        void lunarFallsUnderOneSixthOfTheOverworldsGravity() {
            assertEquals(ONE_SIXTH, LunarGravity.scaleIn(Lunar.LEVEL), EXACT);
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.lunar.LunarGravityTest#everyOtherDimension")
        void everyOtherDimensionKeepsTheOverworldsGravity(ResourceKey<Level> dimension) {
            assertEquals(1.0, LunarGravity.scaleIn(dimension), EXACT);
        }
    }

    @Nested
    class ThrownBlob {
        @Test
        void aThrowInLunarFloatsTheSquareRootOfSixTimesLonger() {
            assertEquals(LUNAR_FLIGHT_TICKS, LunarGravity.flightTicksIn(Lunar.LEVEL, OVERWORLD_FLIGHT_TICKS));
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.lunar.LunarGravityTest#everyOtherDimension")
        void aThrowElsewhereKeepsItsFlightTime(ResourceKey<Level> dimension) {
            assertEquals(OVERWORLD_FLIGHT_TICKS, LunarGravity.flightTicksIn(dimension, OVERWORLD_FLIGHT_TICKS));
        }
    }
}
