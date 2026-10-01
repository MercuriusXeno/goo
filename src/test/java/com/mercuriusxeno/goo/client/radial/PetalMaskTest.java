package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers a wedge mask's petal shape: a rounded cap at its outer end in place of a circle cut (decision wedges-round-off-like-petals). */
class PetalMaskTest {

    private static final double ARC = 2.0 * Math.PI / 16;
    private static final double START = 3 * ARC;
    private static final double INNER = RadialWheel.HUB_FRACTION;
    private static final double OUTER = 1.0;
    private static final double JUST_INSIDE = 0.001;

    private static boolean insideAt(double angle, double distance) {
        return PetalMask.isInsidePetal(Math.sin(angle) * distance, -Math.cos(angle) * distance,
                START, ARC, INNER, OUTER);
    }

    @Nested
    class OuterEnd {

        @Test
        void centerAngleReachesTheOuterRadius() {
            assertTrue(insideAt(START + ARC / 2, OUTER - JUST_INSIDE));
        }

        @Test
        void startEdgeFallsShortOfTheWheelCircle() {
            assertFalse(insideAt(START + JUST_INSIDE, OUTER - JUST_INSIDE));
        }

        @Test
        void endEdgeFallsShortOfTheWheelCircle() {
            assertFalse(insideAt(START + ARC - JUST_INSIDE, OUTER - JUST_INSIDE));
        }

        @Test
        void pointPastTheOuterRadiusIsOutside() {
            assertFalse(insideAt(START + ARC / 2, OUTER + JUST_INSIDE));
        }
    }

    @Nested
    class Body {

        @Test
        void pointInsideTheInnerRadiusIsOutside() {
            assertFalse(insideAt(START + ARC / 2, INNER - JUST_INSIDE));
        }

        @Test
        void edgeNearTheInnerRadiusIsInside() {
            assertTrue(insideAt(START + JUST_INSIDE, INNER + JUST_INSIDE));
        }

        @Test
        void angleOutsideTheWedgeIsOutside() {
            assertFalse(insideAt(START - JUST_INSIDE, (INNER + OUTER) / 2));
        }
    }

    /** The hub's baked circle fills one color, alpha scaled by coverage. */
    @Nested
    class Fill {

        private static final int SIZE = 64;
        private static final int GREY = 0xFF808080;
        /** A right half-plane: every sub-sample of a pixel right of center is inside. */
        private static final PetalMask.Shape RIGHT_HALF = (x, y) -> x > 0;

        private final int[] pixels = PetalMask.fill(SIZE, RIGHT_HALF, GREY);

        @Test
        void insidePixelsCarryTheColor() {
            assertEquals(GREY, pixels[SIZE - 1]);
        }

        @Test
        void outsidePixelsStayTransparent() {
            for (int py = 0; py < SIZE; py += 3) {
                for (int px = 0; px < SIZE / 2; px += 5) {
                    assertEquals(0, pixels[py * SIZE + px], "pixel " + px + "," + py);
                }
            }
        }
    }

    /**
     * The outline the live petal tessellates and edges along lies on the
     * boundary the contains test agrees with, on the cap, the radial edges
     * of the stem and the inner arc (decision petals-render-the-live-fluid).
     */
    @Nested
    class Outline {

        private static final int SEGMENTS = 12;
        private static final double NUDGE = 1e-6;
        private final PetalMask.Petal petal = new PetalMask.Petal(START, ARC, INNER, OUTER);
        private final List<PetalMask.Point> outline = petal.outline(SEGMENTS);

        /** The outline's points on one side, endpoints dropped so each sits on that side alone. */
        private List<PetalMask.Point> side(int index) {
            return outline.subList(index * SEGMENTS + 1, (index + 1) * SEGMENTS);
        }

        private void assertOnBoundaryAlongRay(PetalMask.Point point, boolean insideIsInward) {
            double angle = RadialWheel.angleOf(point.x(), point.y());
            double distance = Math.hypot(point.x(), point.y());
            double inward = insideIsInward ? -NUDGE : NUDGE;
            PetalMask.Point inside = PetalMask.Point.polar(angle, distance + inward);
            PetalMask.Point outside = PetalMask.Point.polar(angle, distance - inward);
            assertTrue(petal.contains(inside.x(), inside.y()), "inside of " + point);
            assertFalse(petal.contains(outside.x(), outside.y()), "outside of " + point);
        }

        private void assertOnBoundaryAcrossAngle(PetalMask.Point point, double insideTurn) {
            double angle = RadialWheel.angleOf(point.x(), point.y());
            double distance = Math.hypot(point.x(), point.y());
            PetalMask.Point inside = PetalMask.Point.polar(angle + insideTurn, distance);
            PetalMask.Point outside = PetalMask.Point.polar(angle - insideTurn, distance);
            assertTrue(petal.contains(inside.x(), inside.y()), "inside of " + point);
            assertFalse(petal.contains(outside.x(), outside.y()), "outside of " + point);
        }

        @Test
        void innerArcPointsLieOnTheInnerBoundary() {
            assertAll(side(0).stream().map(point -> (Executable) () -> assertOnBoundaryAlongRay(point, false)));
        }

        @Test
        void endEdgePointsLieOnTheStemsEndBoundary() {
            assertAll(side(1).stream().map(point -> (Executable) () -> assertOnBoundaryAcrossAngle(point, -NUDGE)));
        }

        @Test
        void capPointsLieOnTheCapsBoundary() {
            assertAll(side(2).stream().map(point -> (Executable) () -> assertOnBoundaryAlongRay(point, true)));
        }

        @Test
        void startEdgePointsLieOnTheStemsStartBoundary() {
            assertAll(side(3).stream().map(point -> (Executable) () -> assertOnBoundaryAcrossAngle(point, NUDGE)));
        }

        @Test
        void capReachesTheOuterRadiusOnTheCenterAngle() {
            assertEquals(OUTER, petal.reach(START + ARC / 2), 1e-12);
        }
    }

    @Nested
    class HalfTurnWedge {

        @Test
        void keepsTheCircleCutAtItsEdges() {
            double halfTurn = Math.PI;
            double angle = JUST_INSIDE;
            assertTrue(PetalMask.isInsidePetal(Math.sin(angle) * (OUTER - JUST_INSIDE),
                    -Math.cos(angle) * (OUTER - JUST_INSIDE), 0.0, halfTurn, INNER, OUTER));
        }
    }
}
