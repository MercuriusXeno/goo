package com.mercuriusxeno.goo.client.radial;

import net.minecraft.util.ARGB;

/**
 * The pure geometry and fill of one radial wedge's mask: a petal whose
 * outer end is a rounded cap bridging its two radial edges rather than a
 * cut of the wheel's circle (decision wedges-round-off-like-petals), filled
 * with its goo's fluid sprite in place of a flat color (decision
 * wedges-render-fluid-texture) inside a solid edge (decision
 * wedges-take-a-solid-edge).
 *
 * <p>The cap is the circle tangent to both radial edges whose farthest
 * point reaches the outer radius on the wedge's center angle. A wedge at
 * least a half turn wide has no tip to round, so it keeps the circle cut.
 * Coordinates are normalized to the wheel: center 0, rim 1, y down positive.
 */
final class PetalMask {

    /**
     * Mask pixels one sprite pixel covers, so a 16-pixel sprite tiles four
     * times across a 256-pixel wheel.
     */
    static final int TEXEL_SCALE = 4;

    /** Sub-samples per axis for anti-aliasing (4x4 = 16 samples per pixel). */
    private static final int AA_SAMPLES = 4;
    private static final int AA_TOTAL = AA_SAMPLES * AA_SAMPLES;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF = 0.5;
    private static final int MAX_CHANNEL = 255;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

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
     * Rasterizes a shape over a square mask, each covered pixel carrying the
     * sprite's pixel at that wheel position, tiled across the wheel and
     * multiplied by the tint, its alpha scaled by the pixel's coverage.
     *
     * @param size   the mask's side in pixels
     * @param shape  the shape over normalized coordinates
     * @param sprite the pixels the fill tiles
     * @param tint   the ARGB tint each sprite pixel is multiplied by
     * @return the mask's ARGB pixels, row by row; uncovered pixels are 0
     */
    static int[] fill(int size, Shape shape, PixelSource sprite, int tint) {
        return fill(size, shape, sprite, tint, Edge.NONE);
    }

    /**
     * Rasterizes a shape as {@link #fill(int, Shape, PixelSource, int)} does,
     * with every covered pixel within the edge's thickness of the shape's
     * boundary taking the edge color in place of the sprite (decision
     * wedges-take-a-solid-edge).
     *
     * @param size   the mask's side in pixels
     * @param shape  the shape over normalized coordinates
     * @param sprite the pixels the fill tiles
     * @param tint   the ARGB tint each sprite pixel is multiplied by
     * @param edge   the solid edge drawn along the boundary
     * @return the mask's ARGB pixels, row by row; uncovered pixels are 0
     */
    static int[] fill(int size, Shape shape, PixelSource sprite, int tint, Edge edge) {
        int[] pixels = new int[size * size];
        double half = size * HALF;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int hits = countHits(px, py, half, shape);
                if (hits > 0) {
                    boolean onEdge = isOnEdge(shape, edge, toCenter(px, half), toCenter(py, half));
                    pixels[py * size + px] = onEdge ? coverPixel(edge.color(), OPAQUE_WHITE, hits)
                            : coverPixel(tiledPixel(sprite, px, py), tint, hits);
                }
            }
        }
        return pixels;
    }

    private static boolean isOnEdge(Shape shape, Edge edge, double x, double y) {
        return edge.thickness() > 0 && (!shape.contains(x, y) || shape.depth(x, y) <= edge.thickness());
    }

    private static double toCenter(int pixel, double half) {
        return (pixel + HALF - half) / half;
    }

    /**
     * The sprite pixel a mask pixel shows when the sprite tiles across the wheel.
     *
     * @param sprite the pixels the fill tiles
     * @param px     the mask pixel's x
     * @param py     the mask pixel's y
     * @return the sprite's ARGB pixel
     */
    static int tiledPixel(PixelSource sprite, int px, int py) {
        return sprite.pixel(Math.floorMod(px / TEXEL_SCALE, sprite.width()),
                Math.floorMod(py / TEXEL_SCALE, sprite.height()));
    }

    private static int coverPixel(int spritePixel, int tint, int hits) {
        int alpha = multiply(ARGB.alpha(spritePixel), ARGB.alpha(tint)) * hits / AA_TOTAL;
        return ARGB.color(alpha, multiply(ARGB.red(spritePixel), ARGB.red(tint)),
                multiply(ARGB.green(spritePixel), ARGB.green(tint)),
                multiply(ARGB.blue(spritePixel), ARGB.blue(tint)));
    }

    private static int multiply(int channel, int tintChannel) {
        return channel * tintChannel / MAX_CHANNEL;
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

        /**
         * How far an inside point lies from the shape's boundary.
         *
         * @param x normalized x
         * @param y normalized y, down positive
         * @return the distance in normalized units; a shape with no boundary to edge answers infinity
         */
        default double depth(double x, double y) {
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * The solid edge a fill draws along a shape's boundary.
     *
     * @param color     the opaque ARGB edge color
     * @param thickness the edge's width inward from the boundary, in normalized units; 0 draws none
     */
    record Edge(int color, double thickness) {
        /** No edge: the fill reaches the boundary. */
        static final Edge NONE = new Edge(0, 0.0);

        /** The width of a wedge's edge, a fiftieth of the wheel's radius. */
        static final double WEDGE_THICKNESS = 0.02;
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

        @Override
        public double depth(double x, double y) {
            double depth = Math.min(Math.hypot(x, y) - inner,
                    Math.min(rayDistance(x, y, start), rayDistance(x, y, start + arc)));
            if (!isRound()) {
                return Math.min(depth, outer - Math.hypot(x, y));
            }
            return isInsideStem(x, y) ? depth : Math.min(depth, capDepth(x, y));
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

        private static double rayDistance(double x, double y, double angle) {
            double dirX = Math.sin(angle);
            double dirY = -Math.cos(angle);
            return x * dirX + y * dirY <= 0 ? Math.hypot(x, y) : Math.abs(x * dirY - y * dirX);
        }
    }

    /** The pixels a fill tiles: a sprite's first frame, or a solid color. */
    interface PixelSource {
        /**
         * The source's width.
         *
         * @return the width in pixels
         */
        int width();

        /**
         * The source's height.
         *
         * @return the height in pixels
         */
        int height();

        /**
         * One pixel of the source.
         *
         * @param x the pixel's x, within the width
         * @param y the pixel's y, within the height
         * @return the ARGB pixel
         */
        int pixel(int x, int y);

        /**
         * A one-pixel source of a single color.
         *
         * @param argb the color
         * @return the source
         */
        static PixelSource solid(int argb) {
            return new PixelSource() {
                @Override
                public int width() {
                    return 1;
                }

                @Override
                public int height() {
                    return 1;
                }

                @Override
                public int pixel(int x, int y) {
                    return argb;
                }
            };
        }
    }
}
