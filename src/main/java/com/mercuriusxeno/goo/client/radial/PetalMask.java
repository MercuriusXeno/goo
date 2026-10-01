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
 * <p>Each corner of the end is rounded by a fillet: a circle tangent to the
 * radial edge and to the wheel-centered circle at the outer radius, of at
 * most {@link #MAX_CORNER} of the band. Between the two fillets the end runs
 * along that outer circle, a blunt tip. A narrow petal's fillets meet on the
 * center line and become the one circle tangent to both edges, its round
 * tip. A wedge at least a half turn wide has no corners to round, so it
 * keeps the circle cut.
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
    static final int CAP_SEGMENTS_PER_SIDE = 6;
    /**
     * The largest a corner's rounding grows, as a fraction of the band from
     * the hub to the tip, so an ability petal rooted on its type keeps the
     * same round tip, so a wide base keeps rounded corners at
     * its sides and a blunt tip rather than reading as a circle.
     */
    static final double MAX_CORNER = 0.25;
    private static final double QUARTER_TURN = Math.PI * HALF;

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
            double offset = wrap(RadialWheel.angleOf(x, y) - start);
            if (offset >= arc) {
                return false;
            }
            boolean startSide = offset < arc * HALF;
            double fromEdge = startSide ? offset : arc - offset;
            return fromEdge >= cornerTurn() || isInsideCorner(x, y, startSide);
        }

        /**
         * Whether a point in a corner's zone, between the edge and the ray
         * through the fillet's center, clears the corner: short of where the
         * fillet meets the edge, or inside the fillet.
         *
         * @param x         normalized x
         * @param y         normalized y, down positive
         * @param startSide true when the point lies on the start edge's half
         * @return true when the corner's rounding does not cut the point off
         */
        private boolean isInsideCorner(double x, double y, boolean startSide) {
            double edge = startSide ? start : start + arc;
            double alongEdge = x * Math.sin(edge) - y * Math.cos(edge);
            if (alongEdge <= cornerCenterDistance() * Math.cos(cornerTurn())) {
                return true;
            }
            Point center = cornerCenter(startSide);
            return Math.hypot(x - center.x(), y - center.y()) <= cornerRadius();
        }

        /**
         * How far a ray from the wheel's center at an angle within the
         * wedge reaches before it leaves the petal: the far side of a corner's
         * fillet near the edges, the outer radius across the blunt tip.
         * decision petals-render-the-live-fluid
         *
         * @param angle the ray's angle, clockwise from the top, within the wedge
         * @return the distance in normalized units
         */
        double reach(double angle) {
            double offset = angle - start;
            double fromEdge = Math.min(offset, arc - offset);
            double turn = cornerTurn();
            if (fromEdge >= turn) {
                return outer;
            }
            double d = cornerCenterDistance();
            double across = d * Math.sin(turn - fromEdge);
            double radius = cornerRadius();
            return d * Math.cos(turn - fromEdge) + Math.sqrt(Math.max(0.0, radius * radius - across * across));
        }

        /**
         * The petal's far boundary from its start edge to its end edge,
         * sampled evenly by the direction it turns through, so a tight fillet
         * reads as round as the broad tip: the start corner's fillet, the
         * blunt tip along the outer circle, the end corner's fillet.
         * decision wedges-round-off-like-petals
         *
         * @param segments the segments the far boundary is cut into
         * @return segments + 1 points, the first on the start edge, the last on the end edge
         */
        List<Point> capSamples(int segments) {
            double turn = cornerTurn();
            double cornerTurning = QUARTER_TURN + turn;
            double tipTurning = arc - turn - turn;
            double total = cornerTurning + cornerTurning + tipTurning;
            List<Point> points = new ArrayList<>(segments + 1);
            for (int i = 0; i <= segments; i++) {
                double turned = total * i / segments;
                if (turned <= cornerTurning) {
                    points.add(filletPoint(true, start - QUARTER_TURN + turned));
                } else if (turned <= cornerTurning + tipTurning) {
                    points.add(Point.polar(start + turn + turned - cornerTurning, outer));
                } else {
                    points.add(filletPoint(false, start + arc - turn + turned - cornerTurning - tipTurning));
                }
            }
            return points;
        }

        private Point filletPoint(boolean startSide, double filletAngle) {
            Point center = cornerCenter(startSide);
            double radius = cornerRadius();
            return new Point(center.x() + Math.sin(filletAngle) * radius, center.y() - Math.cos(filletAngle) * radius);
        }

        /**
         * The point at the center of the petal's round tip: the fillet's
         * center for a narrow petal whose fillets meet, kept no nearer the hub
         * than midway along the band.
         *
         * @return the tip's center
         */
        Point tipCenter() {
            double midway = (inner + outer) * HALF;
            return Point.polar(start + arc * HALF, Math.max(outer - cornerRadius(), midway));
        }

        /**
         * The center of a corner's fillet.
         *
         * @param startSide true for the start edge's corner, false for the end edge's
         * @return the fillet's center
         */
        Point cornerCenter(boolean startSide) {
            double turn = cornerTurn();
            return Point.polar(startSide ? start + turn : start + arc - turn, cornerCenterDistance());
        }

        /**
         * A corner's fillet radius: the circle tangent to both edges for a
         * narrow petal, at most {@link #MAX_CORNER} of the band for a wide
         * one, none for a wedge at least a half turn wide.
         * decision petal-moves-animate
         * decision wedges-round-off-like-petals
         *
         * @return the radius in normalized units
         */
        double cornerRadius() {
            if (arc >= Math.PI) {
                return 0.0;
            }
            double sinHalf = Math.sin(arc * HALF);
            double tangent = outer * sinHalf / (1.0 + sinHalf);
            return Math.min(tangent, MAX_CORNER * (outer - RadialWheel.HUB_FRACTION));
        }

        /**
         * How far a fillet's center sits from the wheel's center: one radius
         * short of the outer circle it touches.
         *
         * @return the distance in normalized units
         */
        private double cornerCenterDistance() {
            return outer - cornerRadius();
        }

        /**
         * The angle from an edge to its fillet's center, as seen from the
         * wheel's center: half the wedge when the fillets meet on the center line.
         *
         * @return the angle in radians
         */
        private double cornerTurn() {
            double d = cornerCenterDistance();
            return d <= 0 ? 0.0 : Math.min(arc * HALF, Math.asin(Math.min(1.0, cornerRadius() / d)));
        }

        /**
         * The petal's closed outline: the inner arc from the start edge to the
         * end edge, the end edge out to its fillet, the far boundary back to
         * the start edge, and the start edge in to the inner arc.
         * decision petals-render-the-live-fluid
         * decision wedges-round-off-like-petals
         *
         * @param segments the segments each straight or inner side is cut into;
         *                 the far boundary takes {@link #CAP_SEGMENTS_PER_SIDE} times as many
         * @return the outline's points in order, the last joining back to the first
         */
        List<Point> outline(int segments) {
            List<Point> cap = capSamples(segments * CAP_SEGMENTS_PER_SIDE);
            List<Point> points = new ArrayList<>(segments * OUTLINE_SIDES + cap.size());
            for (int i = 0; i < segments; i++) {
                points.add(Point.polar(start + arc * i / segments, inner));
            }
            addSpoke(points, Point.polar(start + arc, inner), cap.getLast(), segments);
            for (int i = cap.size() - 1; i > 0; i--) {
                points.add(cap.get(i));
            }
            addSpoke(points, cap.getFirst(), Point.polar(start, inner), segments);
            return points;
        }

        private static void addSpoke(List<Point> points, Point from, Point to, int segments) {
            for (int i = 0; i < segments; i++) {
                points.add(new Point(from.x() + (to.x() - from.x()) * i / segments,
                        from.y() + (to.y() - from.y()) * i / segments));
            }
        }
    }
}
