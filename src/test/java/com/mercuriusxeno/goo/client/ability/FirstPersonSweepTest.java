package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where the slasher's own Reap slash sits on their screen in first
 * person: the crescent's face standing up as an arch bowing up, its tips below
 * the bottom of the screen, the widest spanning past both sides and reaching
 * near the top, narrowing over its life, tilted by its roll and shifted by its
 * heading. Screen coordinates run -1 to 1 across and up.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class FirstPersonSweepTest {

    private static final double TOLERANCE = 1e-6;
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 RIGHT_OF_SOUTH = new Vec3(-1, 0, 0);
    private static final double TAN_HALF_WIDTH = 1.3;

    private static ReapSlash level(double width, double thickness) {
        return new ReapSlash(Vec3.ZERO, SOUTH, RIGHT_OF_SOUTH, width, thickness, width * 0.8, 2.5, 6);
    }

    private static FirstPersonSweep.ScreenPoint at(ReapSlash slash, double fan, double age, double along,
            double layer) {
        return FirstPersonSweep.pointAt(slash, fan, TAN_HALF_WIDTH, age, along, layer);
    }

    @Nested
    class Arch {

        private final ReapSlash widest = level(5, 4);

        @Test
        void itsTipsComeUpFromBelowTheBottomOfTheScreen() {
            assertTrue(at(widest, 0, 0, 0, 1).y() < -1, "one tip below the bottom");
            assertTrue(at(widest, 0, 0, 1, 1).y() < -1, "the other tip below the bottom");
        }

        @Test
        void theWidestSpansPastBothSidesAndReachesNearTheTop() {
            assertTrue(at(widest, 0, 0, 0, 1).x() < -1, "past the left side");
            assertTrue(at(widest, 0, 0, 1, 1).x() > 1, "past the right side");
            double top = at(widest, 0, 0, 0.5, 1).y();
            assertTrue(top > 0.7 && top < 1, "its top at " + top);
        }

        @Test
        void itBowsUpWithBothEdgesCurvedAndItsBodyThick() {
            double inside = at(widest, 0, 0, 0.5, 0).y();
            double outside = at(widest, 0, 0, 0.5, 1).y();
            assertTrue(inside > at(widest, 0, 0, 0.25, 0).y(), "the inside edge arches");
            assertEquals(4 * 0.25, outside - inside, TOLERANCE);
        }

        @Test
        void aSmallSlashIsASmallerArchLowOnTheScreen() {
            ReapSlash smallest = level(2, 1.5);
            assertTrue(at(smallest, 0, 0, 1, 1).x() < at(widest, 0, 0, 1, 1).x(), "narrower");
            assertTrue(at(smallest, 0, 0, 0.5, 1).y() < 0, "its top under the middle of the screen");
        }
    }

    @Nested
    class Life {

        @Test
        void itStartsBiggerAndNarrowsToSixTenths() {
            ReapSlash slash = level(5, 4);
            double start = at(slash, 0, 0, 1, 1).x() - at(slash, 0, 0, 0, 1).x();
            double end = at(slash, 0, 6, 1, 1).x() - at(slash, 0, 6, 0, 1).x();
            assertEquals(start * 0.6, end, TOLERANCE);
        }
    }

    @Nested
    class RollAndHeading {

        @Test
        void itsRollTiltsTheArch() {
            double roll = Math.toRadians(10);
            Vec3 span = RIGHT_OF_SOUTH.scale(Math.cos(roll)).add(0, Math.sin(roll), 0);
            ReapSlash rolled = new ReapSlash(Vec3.ZERO, SOUTH, span, 5, 4, 4, 2.5, 6);
            assertTrue(at(rolled, 0, 0, 1, 1).y() > at(rolled, 0, 0, 0, 1).y(), "its right tip rises");
        }

        @Test
        void itsHeadingShiftsItAcross() {
            ReapSlash slash = level(5, 4);
            double shift = at(slash, Math.toRadians(15), 0, 0.5, 1).x() - at(slash, 0, 0, 0.5, 1).x();
            assertEquals(Math.tan(Math.toRadians(15)) / TAN_HALF_WIDTH, shift, TOLERANCE);
        }
    }
}
