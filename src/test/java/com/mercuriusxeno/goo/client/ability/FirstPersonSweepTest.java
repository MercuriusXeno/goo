package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the slasher's own Reap slash in their first-person view: a crescent
 * lying flat one eye height under the eye and sweeping outward, its outline
 * fitted to the field of view so that on the screen both its edges are arcs of
 * circles, its hollow's top lands nearly at the bottom, its bow's top a third of
 * the way down from the crosshair and its outside edge leaves through the
 * bottom corners; travelling outward and narrowing over its life, sized by its
 * width, tilted by its roll and turned by its heading. Screen coordinates run
 * -1 to 1 across and up, the crosshair at 0.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class FirstPersonSweepTest {

    private static final double TOLERANCE = 1e-6;
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 RIGHT_OF_SOUTH = new Vec3(-1, 0, 0);
    private static final FirstPersonSweep.View NORMAL = view(70, 16.0 / 9);
    private static final FirstPersonSweep.View WIDE_AND_SQUARE = view(110, 4.0 / 3);
    private static final ReapSlash WIDEST = slash(5, RIGHT_OF_SOUTH);

    private static FirstPersonSweep.View view(double fovDegrees, double aspect) {
        double tanHalfHeight = Math.tan(Math.toRadians(fovDegrees) / 2);
        return new FirstPersonSweep.View(tanHalfHeight * aspect, tanHalfHeight);
    }

    private static ReapSlash slash(double width, Vec3 span) {
        return new ReapSlash(Vec3.ZERO, SOUTH, span, width, width * 0.8, width * 0.8, 2.5, 6);
    }

    private static double screenX(FirstPersonSweep.ViewPoint point, FirstPersonSweep.View view) {
        return point.right() / (point.forward() * view.tanHalfWidth());
    }

    private static double screenY(FirstPersonSweep.ViewPoint point, FirstPersonSweep.View view) {
        return point.up() / (point.forward() * view.tanHalfHeight());
    }

    private static FirstPersonSweep.ViewPoint at(FirstPersonSweep.View view, double along, double layer) {
        return FirstPersonSweep.pointAt(WIDEST, 0, view, 0, along, layer);
    }

    @Nested
    class OnTheScreen {

        @Test
        void theBowsTopAndTheHollowsTopLandWhereChosenAtAnyFieldOfView() {
            for (FirstPersonSweep.View view : new FirstPersonSweep.View[] {NORMAL, WIDE_AND_SQUARE}) {
                assertEquals(-0.36, screenY(at(view, 0.5, 1), view), TOLERANCE, "the bow's top");
                assertEquals(-0.92, screenY(at(view, 0.5, 0), view), TOLERANCE, "the hollow's top");
                assertEquals(0, screenX(at(view, 0.5, 1), view), TOLERANCE, "centred on the crosshair");
            }
        }

        @Test
        void theOutsideEdgeLeavesThroughTheBottomCornersAtAnyFieldOfView() {
            for (FirstPersonSweep.View view : new FirstPersonSweep.View[] {NORMAL, WIDE_AND_SQUARE}) {
                double acrossAtTheBottom = 0;
                double nearest = Double.MAX_VALUE;
                for (int step = 0; step <= 4000; step++) {
                    FirstPersonSweep.ViewPoint point = at(view, 0.5 + step / 8000.0, 1);
                    double off = Math.abs(screenY(point, view) + 1);
                    if (off < nearest) {
                        nearest = off;
                        acrossAtTheBottom = screenX(point, view);
                    }
                }
                assertEquals(1, acrossAtTheBottom, 0.005, "the right bottom corner");
            }
        }

        @Test
        void bothEdgesAreArcsOfCirclesOnTheScreen() {
            double aspect = NORMAL.tanHalfWidth() / NORMAL.tanHalfHeight();
            for (double layer : new double[] {0, 1}) {
                double apex = screenY(at(NORMAL, 0.5, layer), NORMAL);
                FirstPersonSweep.ViewPoint first = at(NORMAL, 0.8, layer);
                double firstX = screenX(first, NORMAL) * aspect;
                double firstY = screenY(first, NORMAL);
                double centre = (firstX * firstX + firstY * firstY - apex * apex) / (2 * (firstY - apex));
                for (double along : new double[] {0.03, 0.2, 0.41, 0.66, 0.97}) {
                    FirstPersonSweep.ViewPoint point = at(NORMAL, along, layer);
                    double fromCentre = Math.hypot(screenX(point, NORMAL) * aspect, screenY(point, NORMAL) - centre);
                    assertEquals(apex - centre, fromCentre, TOLERANCE, "layer " + layer + " along " + along);
                }
            }
        }

        @Test
        void itsTipsMeetBelowTheBottomOfTheScreen() {
            for (double tip : new double[] {0, 1}) {
                FirstPersonSweep.ViewPoint inside = at(NORMAL, tip, 0);
                FirstPersonSweep.ViewPoint outside = at(NORMAL, tip, 1);
                assertEquals(inside.right(), outside.right(), TOLERANCE);
                assertEquals(inside.forward(), outside.forward(), TOLERANCE);
                assertTrue(screenY(inside, NORMAL) < -1, "the tip stands below the bottom");
            }
        }
    }

    @Nested
    class LyingFlat {

        @Test
        void everyPointLiesOneEyeHeightUnderTheEyeAndAhead() {
            for (double along : new double[] {0, 0.1, 0.5, 0.9, 1}) {
                for (double layer : new double[] {0, 0.5, 1}) {
                    for (double age : new double[] {0, 3, 6}) {
                        FirstPersonSweep.ViewPoint point = FirstPersonSweep.pointAt(WIDEST, 0, NORMAL, age, along, layer);
                        assertEquals(-1, point.up(), TOLERANCE);
                        assertTrue(point.forward() > 0, "ahead of the eye");
                    }
                }
            }
        }
    }

    @Nested
    class Life {

        @Test
        void itTravelsOutwardAndNarrowsToSixTenths() {
            double hollow = 1 / (NORMAL.tanHalfHeight() * 0.92);
            double far = 1 / (NORMAL.tanHalfHeight() * 0.36);
            FirstPersonSweep.ViewPoint hollowAtTheEnd = FirstPersonSweep.pointAt(WIDEST, 0, NORMAL, 6, 0.5, 0);
            assertEquals(hollow + 0.31 * far, hollowAtTheEnd.forward(), TOLERANCE);
            double start = at(NORMAL, 1, 1).right() - at(NORMAL, 0, 1).right();
            double end = FirstPersonSweep.pointAt(WIDEST, 0, NORMAL, 6, 1, 1).right()
                    - FirstPersonSweep.pointAt(WIDEST, 0, NORMAL, 6, 0, 1).right();
            assertEquals(start * 0.6, end, TOLERANCE);
        }

        @Test
        void itsBowsTopClosesOnTheCrosshairAsItTravels() {
            double atTheEnd = screenY(FirstPersonSweep.pointAt(WIDEST, 0, NORMAL, 6, 0.5, 1), NORMAL);
            assertTrue(atTheEnd > -0.36 && atTheEnd < 0, "the bow's top at the end, " + atTheEnd);
        }
    }

    @Nested
    class Size {

        @Test
        void aNarrowSlashIsSmallerAboutItsHollow() {
            ReapSlash narrow = slash(2, RIGHT_OF_SOUTH);
            FirstPersonSweep.ViewPoint hollow = FirstPersonSweep.pointAt(narrow, 0, NORMAL, 0, 0.5, 0);
            FirstPersonSweep.ViewPoint top = FirstPersonSweep.pointAt(narrow, 0, NORMAL, 0, 0.5, 1);
            assertEquals(-0.92, screenY(hollow, NORMAL), TOLERANCE);
            assertTrue(screenY(top, NORMAL) < -0.36, "its bow's top stands lower");
            double full = at(NORMAL, 1, 1).right() - at(NORMAL, 0, 1).right();
            double small = FirstPersonSweep.pointAt(narrow, 0, NORMAL, 0, 1, 1).right()
                    - FirstPersonSweep.pointAt(narrow, 0, NORMAL, 0, 0, 1).right();
            assertEquals(full * 0.4, small, TOLERANCE);
        }
    }

    @Nested
    class RollAndHeading {

        @Test
        void itsRollRaisesItsRightEndStillUnderTheEye() {
            double roll = Math.toRadians(10);
            ReapSlash rolled = slash(5, RIGHT_OF_SOUTH.scale(Math.cos(roll)).add(0, Math.sin(roll), 0));
            double rightTip = FirstPersonSweep.pointAt(rolled, 0, NORMAL, 0, 1, 1).up();
            double leftTip = FirstPersonSweep.pointAt(rolled, 0, NORMAL, 0, 0, 1).up();
            assertTrue(rightTip > leftTip, "its right end rises");
            assertTrue(rightTip < 0, "and stays under the eye, at " + rightTip);
        }

        @Test
        void itsHeadingTurnsItsBowAcross() {
            double fan = Math.toRadians(15);
            FirstPersonSweep.ViewPoint top = FirstPersonSweep.pointAt(WIDEST, fan, NORMAL, 0, 0.5, 1);
            assertEquals(Math.tan(fan) / NORMAL.tanHalfWidth(), screenX(top, NORMAL), TOLERANCE);
        }
    }
}
