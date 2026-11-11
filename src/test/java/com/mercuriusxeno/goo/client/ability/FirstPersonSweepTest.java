package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where the slasher's own Reap slash sits in their first-person view,
 * in blocks along the view's right, up and forward: the crescent pointing
 * out, its tips beside the slasher and its bow running out ahead, looked down
 * on from two blocks above as the third-person camera sees it, travelling
 * out and narrowing over its life, tilted by its roll and turned by its
 * heading.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class FirstPersonSweepTest {

    private static final double TOLERANCE = 1e-6;
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 RIGHT_OF_SOUTH = new Vec3(-1, 0, 0);

    private static ReapSlash level(Vec3 span) {
        return new ReapSlash(Vec3.ZERO, SOUTH, span, 5, 4, 4, 2.5, 6);
    }

    private static FirstPersonSweep.ViewPoint at(ReapSlash slash, double fan, double age, double along,
            double layer) {
        return FirstPersonSweep.pointAt(slash, fan, age, along, layer);
    }

    @Nested
    class PointingOut {

        private final ReapSlash slash = level(RIGHT_OF_SOUTH);

        @Test
        void itsTipsStartBesideTheSlasherTwoBlocksBelowTheView() {
            FirstPersonSweep.ViewPoint left = at(slash, 0, 0, 0, 1);
            FirstPersonSweep.ViewPoint right = at(slash, 0, 0, 1, 1);
            assertEquals(-2.5, left.right(), TOLERANCE);
            assertEquals(2.5, right.right(), TOLERANCE);
            assertEquals(0, left.forward(), TOLERANCE);
            assertEquals(-2, left.up(), TOLERANCE);
        }

        @Test
        void itsBowRunsOutAheadWithItsBodyAThicknessDeep() {
            assertEquals(4, at(slash, 0, 0, 0.5, 0).forward(), TOLERANCE);
            assertEquals(8, at(slash, 0, 0, 0.5, 1).forward(), TOLERANCE);
            assertEquals(-2, at(slash, 0, 0, 0.5, 1).up(), TOLERANCE);
        }

        @Test
        void itTravelsOutAndNarrowsOverItsLife() {
            assertEquals(4 + 2.5, at(slash, 0, 6, 0.5, 0).forward(), TOLERANCE);
            double start = at(slash, 0, 0, 1, 1).right() - at(slash, 0, 0, 0, 1).right();
            double end = at(slash, 0, 6, 1, 1).right() - at(slash, 0, 6, 0, 1).right();
            assertEquals(start * 0.6, end, TOLERANCE);
        }
    }

    @Nested
    class RollAndHeading {

        @Test
        void itsRollRaisesOneTipStillBelowTheView() {
            double roll = Math.toRadians(10);
            ReapSlash rolled = level(RIGHT_OF_SOUTH.scale(Math.cos(roll)).add(0, Math.sin(roll), 0));
            double rightTip = at(rolled, 0, 0, 1, 1).up();
            assertTrue(rightTip > at(rolled, 0, 0, 0, 1).up(), "its right tip rises");
            assertTrue(rightTip < 0, "and stays below the view, at " + rightTip);
        }

        @Test
        void itsHeadingTurnsItsBowAcross() {
            double fan = Math.toRadians(15);
            assertEquals(8 * Math.sin(fan), at(level(RIGHT_OF_SOUTH), fan, 0, 0.5, 1).right(), TOLERANCE);
        }
    }
}
