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

        /**
         * Nudges an end point across the end, along the normal its two
         * neighbors give, and finds one side inside and the other outside.
         */
        void assertOnTheEnd(PetalMask.Point before, PetalMask.Point point, PetalMask.Point after) {
            double tx = after.x() - before.x();
            double ty = after.y() - before.y();
            double length = Math.hypot(tx, ty);
            double nx = -ty / length * NUDGE;
            double ny = tx / length * NUDGE;
            PetalMask.Point one = new PetalMask.Point(point.x() + nx, point.y() + ny);
            PetalMask.Point other = new PetalMask.Point(point.x() - nx, point.y() - ny);
            boolean oneInside = petal().contains(one.x(), one.y());
            assertTrue(oneInside != petal().contains(other.x(), other.y()), "on the boundary at " + point);
            // inward is toward the wheel's center along the outline's interior side
            PetalMask.Point inside = oneInside ? one : other;
            assertTrue(Math.hypot(inside.x(), inside.y()) < Math.hypot(point.x(), point.y()) + NUDGE,
                    "inside lies toward the hub at " + point);
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
            List<PetalMask.Point> end = end();
            assertAll(java.util.stream.IntStream.range(1, end.size() - 1).mapToObj(i -> (Executable) () ->
                    assertOnTheEnd(end.get(i - 1), end.get(i), end.get(i + 1))));
        }

        @Test
        void startEdgePointsLieOnTheStemsStartBoundary() {
            assertAll(side(3).stream().map(point -> (Executable) () -> assertOnBoundaryAcrossAngle(point, NUDGE)));
        }

        @Test
        void endPointsStepEvenlyAndTurnSmoothly() {
            List<PetalMask.Point> end = end();
            for (int i = 1; i + 1 < end.size(); i++) {
                double before = Math.atan2(end.get(i).x() - end.get(i - 1).x(), -(end.get(i).y() - end.get(i - 1).y()));
                double after = Math.atan2(end.get(i + 1).x() - end.get(i).x(), -(end.get(i + 1).y() - end.get(i).y()));
                assertTrue(Math.abs(Math.IEEEremainder(after - before, 2 * Math.PI)) <= MAX_CAP_STEP, "turn at " + i);
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
        void cornerFilletsMeetAsOneRoundTip() {
            PetalMask.Point start = petal().cornerCenter(true);
            PetalMask.Point end = petal().cornerCenter(false);
            assertEquals(start.x(), end.x(), 1e-9);
            assertEquals(start.y(), end.y(), 1e-9);
        }

        @Test
        void endCircleIsTangentToTheEdges() {
            PetalMask.Point center = petal().cornerCenter(false);
            // the end runs from the end edge's corner back to the start edge's
            PetalMask.Point corner = end().getFirst();
            double edgeX = Math.sin(START + ARC);
            double edgeY = -Math.cos(START + ARC);
            assertEquals(0.0, (corner.x() - center.x()) * edgeX + (corner.y() - center.y()) * edgeY, 1e-9);
        }
    }

    /**
     * A type base widened over four abilities keeps rounded corners at its
     * sides and a blunt tip rather than a circle inscribed in it (decision petal-moves-animate).
     */
    @Nested
    class WideBaseOutline extends OutlineOf {

        private static final double BASE_ARC = 4 * ARC;
        private static final int TIP_STEPS = 5;

        @Override
        PetalMask.Petal petal() {
            return new PetalMask.Petal(START, BASE_ARC, INNER, RadialWheel.TYPE_BASE_LENGTH);
        }

        @Test
        void cornersRoundAtAQuarterOfTheBand() {
            assertEquals(PetalMask.MAX_CORNER * (RadialWheel.TYPE_BASE_LENGTH - INNER), petal().cornerRadius(), 1e-12);
            PetalMask.Point corner = end().getFirst();
            assertTrue(Math.hypot(corner.x(), corner.y()) < RadialWheel.TYPE_BASE_LENGTH - 1e-3,
                    "the end edge stops short of the tip's radius, rounding into the corner");
        }

        @Test
        void tipBetweenTheCornersIsBluntAlongTheOuterCircle() {
            List<PetalMask.Point> end = end();
            PetalMask.Point middle = end.get(end.size() / 2);
            assertEquals(RadialWheel.TYPE_BASE_LENGTH, Math.hypot(middle.x(), middle.y()), 1e-9);
            // five steps of the 72 either side of the middle stay on the blunt tip, short of the fillets
            PetalMask.Point quarter = end.get(end.size() / 2 - TIP_STEPS);
            assertEquals(RadialWheel.TYPE_BASE_LENGTH, Math.hypot(quarter.x(), quarter.y()), 1e-9);
        }
    }

    /**
     * An ability petal rooted on its type petal starts exactly where the type
     * petal ends, its inner boundary on the type petal's far boundary
     * (decision petal-moves-animate).
     */
    @Nested
    class RootedPetal {

        private static final int ROTATIONS = 200_000;
        /** Within this of the corner's own reach, the card touches it: the tangent point's slope blurs the last digits. */
        private static final double CORNER_TOLERANCE = 1e-6;

        private final PetalMask.Petal base = new PetalMask.Petal(START, 4 * ARC, INNER, RadialWheel.TYPE_BASE_LENGTH);
        private final PetalMask.Petal card = new PetalMask.Petal(START, ARC, base.outer(), OUTER, base);

        @Test
        void innerBoundaryLiesOnTheBasesEnd() {
            for (PetalMask.Point point : card.outline(OutlineOf.SEGMENTS)) {
                double angle = RadialWheel.angleOf(point.x(), point.y());
                double distance = Math.hypot(point.x(), point.y());
                if (distance < base.outer() + 1e-9 && Math.abs(distance - card.innerReach(angle)) < 1e-9) {
                    assertEquals(base.reach(angle), distance, 1e-9);
                }
            }
            double cornerAngle = START + base.cornerRadius() / base.outer() / 2;
            assertTrue(card.innerReach(cornerAngle) < base.outer(), "follows the base's rounded corner in");
        }

        /**
         * At whatever rotation the ring turns to, the outermost cards start on
         * the base's rounded corner at both of its edges, never at its full
         * length: an edge angle that rounds a hair past the base's arc still
         * reads as on it.
         */
        @Test
        void edgeCardsMeetTheBasesCornersAtEveryRotation() {
            int abilities = 4;
            double openness = 1.0;
            for (int step = 0; step < ROTATIONS; step++) {
                // the wheel's own arithmetic: the slot from its rotation, each card from the slot's start
                double rotation = -Math.PI + step * Math.sqrt(2) * 1e-4;
                PetalMask.Petal turnedBase = new PetalMask.Petal(rotation, abilities * ARC, INNER,
                        RadialWheel.TYPE_BASE_LENGTH);
                PetalMask.Petal first = new PetalMask.Petal(rotation, ARC, turnedBase.outer(), OUTER, turnedBase);
                PetalMask.Petal last = new PetalMask.Petal(rotation + (abilities - 1) * ARC * openness, ARC,
                        turnedBase.outer(), OUTER, turnedBase);
                double corner = turnedBase.reach(rotation);
                assertEquals(corner, first.innerReach(first.start()), CORNER_TOLERANCE, "CCW edge at step " + step);
                assertEquals(corner, last.innerReach(last.start() + last.arc()), CORNER_TOLERANCE,
                        "CW edge at step " + step);
            }
        }

        @Test
        void pointJustPastTheBaseIsInsideTheCardAndJustShortIsNot() {
            double angle = START + ARC / 2;
            PetalMask.Point past = PetalMask.Point.polar(angle, base.reach(angle) + 1e-6);
            PetalMask.Point short_ = PetalMask.Point.polar(angle, base.reach(angle) - 1e-6);
            assertTrue(card.contains(past.x(), past.y()));
            assertFalse(card.contains(short_.x(), short_.y()));
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
