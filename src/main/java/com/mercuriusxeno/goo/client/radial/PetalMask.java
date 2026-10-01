package com.mercuriusxeno.goo.client.radial;

import net.minecraft.util.ARGB;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure geometry of one radial petal: a wedge whose outer end is a
 * rounded cap bridging its two radial edges rather than a cut of the
 * wheel's circle (decision wedges-round-off-like-petals), with the outline
 * the live-drawn petal tessellates and edges along (decision
 * petals-render-the-live-fluid), and the rasterizer the hub's baked circle
 * mask still reads.
 *
 * <p>The cap is the circle tangent to both radial edges whose farthest
 * point reaches the outer radius on the wedge's center angle. A wedge at
 * least a half turn wide has no tip to round, so it keeps the circle cut.
 * Coordinates are normalized to the wheel: center 0, rim 1, y down positive.
 */
final class PetalMask {

    /** Sub-samples per axis for anti-aliasing (4x4 = 16 samples per pixel). */
    private static final int AA_SAMPLES = 4;
    private static final int AA_TOTAL = AA_SAMPLES * AA_SAMPLES;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF = 0.5;
    /** The outline's sides: the inner arc, the end edge, the cap and the start edge. */
    private static final int OUTLINE_SIDES = 4;
    /** How many times more segments the cap takes than a straight side, so the round tip reads smooth. */
    static final int CAP_SEGMENTS_PER_SIDE = 4;
    /**
     * The most a wide petal's end bulges past its corners, as a fraction of
     * its band from the inner radius to the tip, so a wide base reads as a
     * petal rather than a circle inscribed in it.
     */
    static final double MAX_BULGE = 0.25;
    private static final double TWICE = 2.0;

    private PetalMask() {
    }

    /**
     * Whether a point lies inside a wedge's petal.
     *
     * @param x          normalized x (-1..1, center = 0)
     * @param y          normalized y (-1..1, center = 0, down positive)
     * @param startAngle the wedge's start, clockwise from the top, in [0, 2 pi)
     * @param arc        the wedge's span in radians
     * @param innerNorm  the wedge's inner radius
     * @param outerNorm  the wedge's outer radius, reached at the tip of the cap
     * @return true if the point is inside the petal
     */
    static boolean isInsidePetal(double x, double y, double startAngle, double arc,
                                 double innerNorm, double outerNorm) {
        return new Petal(startAngle, arc, innerNorm, outerNorm).contains(x, y);
    }

    /**
     * Rasterizes a shape over a square mask in one color, each covered
     * pixel's alpha scaled by the pixel's coverage.
     *
     * @param size  the mask's side in pixels
     * @param shape the shape over normalized coordinates
     * @param color the ARGB color of a fully covered pixel
     * @return the mask's ARGB pixels, row by row; uncovered pixels are 0
     */
    static int[] fill(int size, Shape shape, int color) {
        int[] pixels = new int[size * size];
        double half = size * HALF;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int hits = countHits(px, py, half, shape);
                if (hits > 0) {
                    pixels[py * size + px] = ARGB.color(ARGB.alpha(color) * hits / AA_TOTAL, color);
                }
            }
        }
        return pixels;
    }

    private static int countHits(int px, int py, double half, Shape shape) {
        int hits = 0;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                if (shape.contains(toNormalized(px, sx, half), toNormalized(py, sy, half))) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private static double toNormalized(int pixel, int sample, double half) {
        return (pixel + (sample + HALF) / AA_SAMPLES - half) / half;
    }

    private static double wrap(double angle) {
        double wrapped = angle % TWO_PI;
        return wrapped < 0 ? wrapped + TWO_PI : wrapped;
    }

    /** A mask's shape over normalized coordinates. */
    @FunctionalInterface
    interface Shape {
        /**
         * Whether a normalized point lies inside the shape.
         *
         * @param x normalized x
         * @param y normalized y, down positive
         * @return true when inside
         */
        boolean contains(double x, double y);
    }

    /**
     * A point in the wheel's normalized coordinates.
     *
     * @param x normalized x
     * @param y normalized y, down positive
     */
    record Point(double x, double y) {

        /**
         * The point at an angle and a distance from the wheel's center.
         *
         * @param angle    the angle, clockwise from the top
         * @param distance the distance from the center
         * @return the point
         */
        static Point polar(double angle, double distance) {
            return new Point(Math.sin(angle) * distance, -Math.cos(angle) * distance);
        }
    }

    /**
     * One wedge's petal.
     *
     * @param start the wedge's start, clockwise from the top, in [0, 2 pi)
     * @param arc   the wedge's span in radians
     * @param inner the wedge's inner radius
     * @param outer the wedge's outer radius, reached at the tip of the cap
     */
    record Petal(double start, double arc, double inner, double outer) implements Shape {

        @Override
        public boolean contains(double x, double y) {
            double distance = Math.hypot(x, y);
            if (distance < inner || distance > outer) {
                return false;
            }
            if (wrap(RadialWheel.angleOf(x, y) - start) >= arc) {
                return false;
            }
            return !isRound() || isInsideStem(x, y) || capDepth(x, y) >= 0;
        }

        /**
         * How far a ray from the wheel's center at an angle within the
         * wedge reaches before it leaves the petal: the far side of the cap
         * circle, or the outer radius for a wedge with no cap.
         * decision petals-render-the-live-fluid
         *
         * @param angle the ray's angle, clockwise from the top, within the wedge
         * @return the distance in normalized units
         */
        double reach(double angle) {
            if (!isRound()) {
                return outer;
            }
            double offCenter = angle - (start + arc * HALF);
            double capCenter = capCenter();
            double capRadius = capRadius();
            double across = capCenter * Math.sin(offCenter);
            return capCenter * Math.cos(offCenter) + Math.sqrt(Math.max(0.0, capRadius * capRadius - across * across));
        }

        /**
         * The petal's far boundary from its start edge to its end edge,
         * sampled evenly around the cap circle itself, so the tip reads round
         * where the cap meets the edges; a wedge with no cap samples its outer
         * arc evenly by angle.
         * decision wedges-round-off-like-petals
         *
         * @param segments the segments the far boundary is cut into
         * @return segments + 1 points, the first on the start edge, the last on the end edge
         */
        List<Point> capSamples(int segments) {
            List<Point> points = new ArrayList<>(segments + 1);
            for (int i = 0; i <= segments; i++) {
                points.add(isRound() ? capPoint(capTurnStart() + capTurn() * i / segments)
                        : Point.polar(start + arc * i / segments, outer));
            }
            return points;
        }

        /**
         * The point at the center of the petal's round tip: the cap circle's
         * center, kept no nearer the hub than midway along the band, so a
         * shallow end's far-off circle center still leaves the point inside.
         *
         * @return the tip's center
         */
        Point tipCenter() {
            double center = start + arc * HALF;
            double midway = (inner + outer) * HALF;
            return Point.polar(center, isRound() ? Math.max(capCenter(), midway) : midway);
        }

        /**
         * The center of the circle the petal's end follows.
         *
         * @return the point on the center line at the cap circle's center
         */
        Point capCircleCenter() {
            return Point.polar(start + arc * HALF, capCenter());
        }

        /**
         * The petal's closed outline: the inner arc from the start edge to the
         * end edge, the end edge out to the cap, the cap back around its own
         * circle to the start edge, and the start edge in to the inner arc.
         * decision petals-render-the-live-fluid
         * decision wedges-round-off-like-petals
         *
         * @param segments the segments each straight or inner side is cut into;
         *                 the cap takes {@link #CAP_SEGMENTS_PER_SIDE} times as many
         * @return the outline's points in order, the last joining back to the first
         */
        List<Point> outline(int segments) {
            List<Point> cap = capSamples(segments * CAP_SEGMENTS_PER_SIDE);
            List<Point> points = new ArrayList<>(segments * OUTLINE_SIDES + cap.size());
            Point endTip = cap.getLast();
            Point startTip = cap.getFirst();
            for (int i = 0; i < segments; i++) {
                points.add(Point.polar(start + arc * i / segments, inner));
            }
            addSpoke(points, Point.polar(start + arc, inner), endTip, segments);
            for (int i = cap.size() - 1; i > 0; i--) {
                points.add(cap.get(i));
            }
            addSpoke(points, startTip, Point.polar(start, inner), segments);
            return points;
        }

        private static void addSpoke(List<Point> points, Point from, Point to, int segments) {
            for (int i = 0; i < segments; i++) {
                points.add(new Point(from.x() + (to.x() - from.x()) * i / segments,
                        from.y() + (to.y() - from.y()) * i / segments));
            }
        }

        /**
         * The cap circle's own angle, clockwise from the top, where it meets
         * the start edge.
         *
         * @return the angle in radians
         */
        private double capTurnStart() {
            return start + arc * HALF - capHalfTurn();
        }

        /**
         * The turn the cap circle's far side spans between the two edges.
         *
         * @return twice the half turn from the tip to either edge
         */
        private double capTurn() {
            return TWICE * capHalfTurn();
        }

        /**
         * The turn around the cap circle from the tip to where it meets an
         * edge: a quarter turn and a half wedge for a cap tangent to the
         * edges, less for a shallower end.
         *
         * @return the angle in radians
         */
        private double capHalfTurn() {
            double corner = cornerDistance();
            return Math.atan2(corner * sinHalf(), corner * cosHalf() - capCenter());
        }

        private Point capPoint(double capAngle) {
            double center = start + arc * HALF;
            double capCenter = capCenter();
            double capRadius = capRadius();
            return new Point(Math.sin(center) * capCenter + Math.sin(capAngle) * capRadius,
                    -Math.cos(center) * capCenter - Math.cos(capAngle) * capRadius);
        }

        /**
         * Whether the wedge ends in a cap; one at least a half turn wide has no tip to round.
         *
         * @return true for a wedge narrower than a half turn
         */
        private boolean isRound() {
            return arc < Math.PI;
        }

        private double sinHalf() {
            return Math.sin(arc * HALF);
        }

        private double cosHalf() {
            return Math.cos(arc * HALF);
        }

        /**
         * How far the end bulges past where the edges stop, along the center
         * line: the circle tangent to both edges for a narrow petal, flattened
         * to at most {@link #MAX_BULGE} of the band for a wide one, never
         * flatter than the wheel's own circle at the tip.
         * decision petal-moves-animate
         * decision wedges-round-off-like-petals
         *
         * @return the bulge in normalized units
         */
        private double bulge() {
            double tangent = outer * sinHalf();
            double wheel = outer * (1.0 - cosHalf());
            return Math.max(wheel, Math.min(tangent, MAX_BULGE * (outer - inner)));
        }

        /**
         * How far along each edge the end begins.
         *
         * @return the distance from the wheel's center in normalized units
         */
        private double cornerDistance() {
            return (outer - bulge()) / cosHalf();
        }

        /**
         * Distance from the wheel's center to the cap circle's center, along the
         * wedge's center angle: the circle through the tip and both corners.
         *
         * @return the distance in normalized units
         */
        private double capCenter() {
            double bulge = bulge();
            if (bulge <= 0) {
                return 0.0;
            }
            double corner = cornerDistance();
            return (outer * outer - corner * corner) / (TWICE * bulge);
        }

        private double capRadius() {
            return outer - capCenter();
        }

        /**
         * Whether a point lies short of the line joining the end's two corners.
         *
         * @param x normalized x
         * @param y normalized y, down positive
         * @return true on the wheel's center side of that line
         */
        private boolean isInsideStem(double x, double y) {
            return along(x, y) <= outer - bulge();
        }

        private double along(double x, double y) {
            double center = start + arc * HALF;
            return x * Math.sin(center) - y * Math.cos(center);
        }

        /**
         * How far inside the cap circle a point lies.
         *
         * @param x normalized x
         * @param y normalized y, down positive
         * @return the distance to the cap circle, negative outside it
         */
        private double capDepth(double x, double y) {
            double center = start + arc * HALF;
            double capCenter = capCenter();
            return capRadius()
                    - Math.hypot(x - Math.sin(center) * capCenter, y + Math.cos(center) * capCenter);
        }
    }
}
