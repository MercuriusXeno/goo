package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Astral's gate looks for the moon in the overworld and for the blue overworld star in lunar, and finds neither
 * where the look strays, where the moon has set, or in any other dimension.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 */
class GateSightTest {

    private static final double TOLERANCE = 1e-6;
    /** The moon at midnight stands straight overhead. */
    private static final float MIDNIGHT = 0F;
    /** A quarter turn on, the moon stands on the western horizon; vanilla's sky turns it toward negative x. */
    private static final float MOONSET = 90F;
    /** Half a turn on, the moon stands straight below. */
    private static final float NOON = 180F;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 DOWN = new Vec3(0, -1, 0);
    /** A look a few degrees off straight up, inside the gate's reach. */
    private static final Vec3 NEARLY_UP = new Vec3(0.05, 1, 0).normalize();
    /** A look well off straight up, outside it. */
    private static final Vec3 WELL_OFF_UP = new Vec3(0.5, 1, 0).normalize();

    @Nested
    class TheMoon {

        @Test
        void standsOverheadAtMidnight() {
            Vec3 moon = GateSight.moonDirection(MIDNIGHT);
            assertEquals(0.0, moon.x(), TOLERANCE);
            assertEquals(1.0, moon.y(), TOLERANCE);
            assertEquals(0.0, moon.z(), TOLERANCE);
        }

        @Test
        void turnsTowardNegativeXAsItSets() {
            Vec3 moon = GateSight.moonDirection(MOONSET);
            assertEquals(-1.0, moon.x(), TOLERANCE);
            assertEquals(0.0, moon.y(), TOLERANCE);
        }

        @Test
        void aLookAtTheMoonInTheOverworldFindsIt() {
            assertEquals(Optional.of(GateSight.Sky.MOON), GateSight.skyAt(Level.OVERWORLD, NEARLY_UP, MIDNIGHT));
        }

        @Test
        void aLookWellOffTheMoonFindsNothing() {
            assertEquals(Optional.empty(), GateSight.skyAt(Level.OVERWORLD, WELL_OFF_UP, MIDNIGHT));
        }

        @Test
        void aMoonBelowTheHorizonIsNeverFound() {
            assertEquals(Optional.empty(), GateSight.skyAt(Level.OVERWORLD, DOWN, NOON));
        }

        @Test
        void theMoonIsFoundInTheOverworldAlone() {
            assertEquals(Optional.empty(), GateSight.skyAt(Level.NETHER, UP, MIDNIGHT));
        }
    }

    @Nested
    class TheOverworldStar {

        @Test
        void aLookAtTheBlueStarInLunarFindsIt() {
            assertEquals(Optional.of(GateSight.Sky.OVERWORLD_STAR),
                    GateSight.skyAt(Lunar.LEVEL, Lunar.OVERWORLD_STAR_DIRECTION, MIDNIGHT));
        }

        @Test
        void aLookAwayFromTheBlueStarInLunarFindsNothing() {
            assertEquals(Optional.empty(), GateSight.skyAt(Lunar.LEVEL, DOWN, MIDNIGHT));
        }

        @Test
        void lunarShowsNoMoon() {
            assertEquals(Optional.empty(), GateSight.skyAt(Lunar.LEVEL, UP, MIDNIGHT));
        }
    }
}
