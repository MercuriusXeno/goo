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
     * boundary the contains test agrees with, on the end, the radial edges
     * of the stem and the inner arc (decisions petals-render-the-live-fluid,
     * wedges-round-off-like-petals).
     */
    abstract class OutlineOf {

        static final int SEGMENTS = 12;
        static final int CAP_POINTS = SEGMENTS * PetalMask.CAP_SEGMENTS_PER_SIDE;
        /** The widest step around the end's circle between consecutive outline points. */
        static final double MAX_CAP_STEP = Math.toRadians(5.0);
        static final double NUDGE = 1e-6;

        abstract PetalMask.Petal petal();

        List<PetalMask.Point> outline() {
            return petal().outline(SEGMENTS);
        }

        /** The outline's points on one side, endpoints dropped so each sits on that side alone. */
        List<PetalMask.Point> side(int index) {
            int[] starts = {0, SEGMENTS, 2 * SEGMENTS, 2 * SEGMENTS + CAP_POINTS, 3 * SEGMENTS + CAP_POINTS};
            return outline().subList(starts[index] + 1, starts[index + 1]);
        }

        /** The end's points from corner to corner. */
        List<PetalMask.Point> end() {
            return outline().subList(2 * SEGMENTS, 2 * SEGMENTS + CAP_POINTS + 1);
        }

        void assertInsideAndOutside(PetalMask.Point inside, PetalMask.Point outside, PetalMask.Point point) {
            assertTrue(petal().contains(inside.x(), inside.y()), "inside of " + point);
            assertFalse(petal().contains(outside.x(), outside.y()), "outside of " + point);
        }

        /** Nudges an end point toward and away from the end circle's center. */
        void assertOnTheEndCircle(PetalMask.Point point) {
            PetalMask.Point center = petal().capCircleCenter();
            double dx = point.x() - center.x();
            double dy = point.y() - center.y();
            double radius = Math.hypot(dx, dy);
            double in = (radius - NUDGE) / radius;
            double out = (radius + NUDGE) / radius;
            assertInsideAndOutside(new PetalMask.Point(center.x() + dx * in, center.y() + dy * in),
                    new PetalMask.Point(center.x() + dx * out, center.y() + dy * out), point);
        }

        void assertOnBoundaryAlongRay(PetalMask.Point point) {
            double angle = RadialWheel.angleOf(point.x(), point.y());
            double distance = Math.hypot(point.x(), point.y());
            assertInsideAndOutside(PetalMask.Point.polar(angle, distance + NUDGE),
                    PetalMask.Point.polar(angle, distance - NUDGE), point);
        }

        void assertOnBoundaryAcrossAngle(PetalMask.Point point, double insideTurn) {
            double angle = RadialWheel.angleOf(point.x(), point.y());
            double distance = Math.hypot(point.x(), point.y());
            assertInsideAndOutside(PetalMask.Point.polar(angle + insideTurn, distance),
                    PetalMask.Point.polar(angle - insideTurn, distance), point);
        }

        /** How far the end's tip reaches past its corners, along the center line. */
        double bulge() {
            PetalMask.Petal petal = petal();
            PetalMask.Point corner = end().getFirst();
            double axis = petal.start() + petal.arc() / 2;
            return petal.outer() - (corner.x() * Math.sin(axis) - corner.y() * Math.cos(axis));
        }

        @Test
        void innerArcPointsLieOnTheInnerBoundary() {
            assertAll(side(0).stream().map(point -> (Executable) () -> assertOnBoundaryAlongRay(point)));
        }

        @Test
        void endEdgePointsLieOnTheStemsEndBoundary() {
            assertAll(side(1).stream().map(point -> (Executable) () -> assertOnBoundaryAcrossAngle(point, -NUDGE)));
        }

        @Test
        void endPointsLieOnTheEndsBoundary() {
            assertAll(side(2).stream().map(point -> (Executable) () -> assertOnTheEndCircle(point)));
        }

        @Test
        void startEdgePointsLieOnTheStemsStartBoundary() {
            assertAll(side(3).stream().map(point -> (Executable) () -> assertOnBoundaryAcrossAngle(point, NUDGE)));
        }

        @Test
        void endPointsStepEvenlyAroundTheEndCircle() {
            PetalMask.Point center = petal().capCircleCenter();
            List<PetalMask.Point> end = end();
            for (int i = 0; i + 1 < end.size(); i++) {
                double from = Math.atan2(end.get(i).x() - center.x(), -(end.get(i).y() - center.y()));
                double to = Math.atan2(end.get(i + 1).x() - center.x(), -(end.get(i + 1).y() - center.y()));
                assertTrue(Math.abs(Math.IEEEremainder(to - from, 2 * Math.PI)) <= MAX_CAP_STEP, "step " + i);
            }
        }

        @Test
        void endReachesTheOuterRadiusOnTheCenterAngle() {
            PetalMask.Petal petal = petal();
            assertEquals(petal.outer(), petal.reach(petal.start() + petal.arc() / 2), 1e-12);
        }
    }

    /** An ability-sized petal ends in the circle tangent to its edges. */
    @Nested
    class NarrowPetalOutline extends OutlineOf {

        @Override
        PetalMask.Petal petal() {
            return new PetalMask.Petal(START, ARC, INNER, OUTER);
        }

        @Test
        void endCircleIsTangentToTheEdges() {
            PetalMask.Point center = petal().capCircleCenter();
            // the end runs from the end edge's corner back to the start edge's
            PetalMask.Point corner = end().getFirst();
            double edgeX = Math.sin(START + ARC);
            double edgeY = -Math.cos(START + ARC);
            assertEquals(0.0, (corner.x() - center.x()) * edgeX + (corner.y() - center.y()) * edgeY, 1e-9);
        }
    }

    /**
     * A type base widened over four abilities ends in a shallow arc across its
     * whole width rather than a circle inscribed in it (decision petal-moves-animate).
     */
    @Nested
    class WideBaseOutline extends OutlineOf {

        private static final double BASE_ARC = 4 * ARC;

        @Override
        PetalMask.Petal petal() {
            return new PetalMask.Petal(START, BASE_ARC, INNER, RadialWheel.TYPE_BASE_LENGTH);
        }

        @Test
        void endBulgesNoMoreThanAQuarterOfItsBandOrTheWheelsOwnCurve() {
            double length = RadialWheel.TYPE_BASE_LENGTH;
            double wheelCurve = length * (1 - Math.cos(BASE_ARC / 2));
            assertTrue(bulge() <= Math.max(PetalMask.MAX_BULGE * (length - INNER), wheelCurve) + 1e-9,
                    "bulge " + bulge());
            assertTrue(bulge() < length * Math.sin(BASE_ARC / 2), "shallower than the inscribed circle");
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
