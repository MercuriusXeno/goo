package com.mercuriusxeno.goo.client.ability;

/**
 * The blur that makes the drink's splatted spheres one skin, and the normal
 * the shade pass reads off it, stated in Java: drink_blur.fsh runs the same
 * kernel and drink_shade.fsh the same normal, and DrinkShaderTest holds their
 * constants to these. Each blur pass averages a pixel's distance from the
 * eye and its color with its covered neighbours along one screen axis, the
 * kernel as wide on screen as the sphere there is times
 * {@link #WIDTH_SHARE}, so near spheres blend as widely as far ones; a
 * neighbour farther in depth than the sphere's radius times
 * {@link #DEPTH_SHARE} weighs nothing, so the silhouette against the world
 * and between a near and a far stream stays sharp, and a pixel no splat
 * covered keeps the world's depth untouched. The kernel reads every pixel up
 * to {@link #MOST_TAPS} either side and spreads its taps evenly past that,
 * each tap read between its two nearest pixels: taps a whole stride apart,
 * read at the nearest pixel, stepped the slope each time one crossed the
 * join between two spheres, a sawtooth in the normal as wide as the stride.
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
public final class DrinkBlur {

    /** The most taps the kernel reads either side of a pixel; a narrower kernel reads every pixel it covers. */
    public static final int MOST_TAPS = 24;
    /** The kernel's half width on screen, as a share of the sphere's radius there. */
    public static final double WIDTH_SHARE = 1.0;
    /** The depth difference past which a neighbour weighs nothing, as a share of the sphere's radius. */
    public static final double DEPTH_SHARE = 1.0;
    /** The radius a splat's coverage channel reads 1 at: the block's own half width, the widest a splat is. */
    public static final double RADIUS_SCALE = DrinkTree.MOUTH;
    /** The coverage under which a pixel holds no splat: half the coverage channel's least step. */
    public static final double COVERED = 0.002;
    /** How fast the kernel's weight falls toward its edge: a gaussian whose deviation is half its width. */
    private static final double SPATIAL = 2;

    private DrinkBlur() {
    }

    /**
     * @param radius         the sphere's radius at a pixel, in blocks
     * @param pixelsPerBlock how many pixels a block spans on screen at that pixel's depth
     * @return the kernel's half width there, in pixels, never under one
     */
    public static double widthOf(double radius, double pixelsPerBlock) {
        return Math.max(1, WIDTH_SHARE * radius * pixelsPerBlock);
    }

    /**
     * @param width the kernel's half width, in pixels
     * @return how many taps it reads either side: one a pixel, up to {@link #MOST_TAPS}
     */
    public static int tapsOf(double width) {
        return (int) Math.min(MOST_TAPS, Math.ceil(width));
    }

    /**
     * @param tap        which tap, from {@code -taps} to {@code taps}
     * @param taps       how many taps the kernel reads either side
     * @param depthDelta how much farther the neighbour is than the pixel, in blocks
     * @param threshold  the depth difference at which a neighbour weighs nothing, in blocks
     * @return the neighbour's weight: a gaussian over the kernel, falling smoothly to nothing at the threshold
     */
    public static double weightOf(int tap, int taps, double depthDelta, double threshold) {
        double share = (double) tap / taps;
        double near = depthDelta / threshold;
        double range = Math.abs(near) >= 1 ? 0 : (1 - near * near) * (1 - near * near);
        return Math.exp(-SPATIAL * share * share) * range;
    }

    /**
     * One blur pass along a row of pixels, as the shader runs it: each
     * covered pixel's distance averaged over the covered neighbours its
     * kernel reaches; an uncovered pixel kept as it was.
     *
     * @param distance       each pixel's distance from the eye, in blocks
     * @param radius         each pixel's sphere radius, in blocks, 0 where no splat covers it
     * @param pixelsPerBlock how many pixels a block spans on screen, the same along the row
     * @return the blurred distances
     */
    public static double[] blurRow(double[] distance, double[] radius, double pixelsPerBlock) {
        double[] blurred = distance.clone();
        for (int pixel = 0; pixel < distance.length; pixel++) {
            if (radius[pixel] > 0) {
                blurred[pixel] = blurredAt(distance, radius, pixel, pixelsPerBlock);
            }
        }
        return blurred;
    }

    private static double blurredAt(double[] distance, double[] radius, int pixel, double pixelsPerBlock) {
        double width = widthOf(radius[pixel], pixelsPerBlock);
        int taps = tapsOf(width);
        double stride = width / taps;
        double threshold = DEPTH_SHARE * radius[pixel];
        double weights = 0;
        double sum = 0;
        for (int tap = -taps; tap <= taps; tap++) {
            double there = readAt(distance, radius, pixel + tap * stride);
            if (!Double.isNaN(there)) {
                double weight = weightOf(tap, taps, there - distance[pixel], threshold);
                weights += weight;
                sum += weight * there;
            }
        }
        return sum / weights;
    }

    /**
     * @param distance each pixel's distance from the eye
     * @param radius   each pixel's sphere radius, 0 where uncovered
     * @param at       a place along the row, in pixels
     * @return the distance there, read between its two nearest pixels where both are covered and at the covered one
     *         where only one is, or NaN where neither is
     */
    static double readAt(double[] distance, double[] radius, double at) {
        int low = (int) Math.clamp(Math.floor(at), 0, distance.length - 1);
        int high = Math.min(low + 1, distance.length - 1);
        double share = Math.clamp(at - low, 0, 1);
        if (radius[low] > 0 && radius[high] > 0) {
            return distance[low] + (distance[high] - distance[low]) * share;
        }
        if (radius[low] > 0) {
            return distance[low];
        }
        return radius[high] > 0 ? distance[high] : Double.NaN;
    }

    /**
     * The change in distance across a pixel that the shade's normal is built
     * from: the smaller of the steps to its two neighbours, so a pixel at an
     * edge takes its slope from the side it shares a surface with rather than
     * across the drop to the world behind.
     *
     * @param before the distance at the neighbour before it
     * @param at     the distance at the pixel
     * @param after  the distance at the neighbour after it
     * @return the step, in blocks a pixel
     */
    public static double slopeAt(double before, double at, double after) {
        double forward = after - at;
        double backward = at - before;
        return Math.abs(forward) < Math.abs(backward) ? forward : backward;
    }
}
