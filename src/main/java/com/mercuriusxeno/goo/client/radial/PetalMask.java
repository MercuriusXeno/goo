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
            double capRadius = capCenter * sinHalf();
            double across = capCenter * Math.sin(offCenter);
            return capCenter * Math.cos(offCenter) + Math.sqrt(Math.max(0.0, capRadius * capRadius - across * across));
        }

        /**
         * The petal's closed outline: the inner arc from the start edge to the
         * end edge, the end edge out to the cap, the cap back to the start
         * edge, and the start edge in to the inner arc.
         * decision petals-render-the-live-fluid
         *
         * @param segments the segments each of the four sides is cut into
         * @return the outline's points in order, the last joining back to the first
         */
        List<Point> outline(int segments) {
            List<Point> points = new ArrayList<>(segments * OUTLINE_SIDES);
            double end = start + arc;
            for (int i = 0; i < segments; i++) {
                points.add(Point.polar(start + arc * i / segments, inner));
            }
            for (int i = 0; i < segments; i++) {
                points.add(Point.polar(end, inner + (reach(end) - inner) * i / segments));
            }
            for (int i = 0; i < segments; i++) {
                double angle = end - arc * i / segments;
                points.add(Point.polar(angle, reach(angle)));
            }
            for (int i = 0; i < segments; i++) {
                points.add(Point.polar(start, reach(start) - (reach(start) - inner) * i / segments));
            }
            return points;
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

        /**
         * Distance from the wheel's center to the cap circle's center, along the wedge's center angle.
         *
         * @return the distance in normalized units
         */
        private double capCenter() {
            return outer / (1.0 + sinHalf());
        }

        /**
         * Whether a point lies short of the line joining the cap's two tangent points.
         *
         * @param x normalized x
         * @param y normalized y, down positive
         * @return true on the wheel's center side of that line
         */
        private boolean isInsideStem(double x, double y) {
            double cosHalf = Math.cos(arc * HALF);
            return along(x, y) <= capCenter() * cosHalf * cosHalf;
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
            return capCenter * sinHalf()
                    - Math.hypot(x - Math.sin(center) * capCenter, y + Math.cos(center) * capCenter);
        }
    }
}
