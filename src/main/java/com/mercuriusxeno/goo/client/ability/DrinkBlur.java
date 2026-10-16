package com.mercuriusxeno.goo.client.ability;

/**
 * The blur that makes the drink's splatted spheres one skin, and the normal
 * the shade pass reads off it, stated in Java: drink_blur.fsh runs the same
 * kernel and drink_shade.fsh the same normal, and DrinkShaderTest holds their
 * constants to these. Each blur pass averages a pixel's distance from the
 * eye and its color with its covered neighbours along one screen axis, the
 * kernel as wide on screen as the sphere there is times
 * {@link #WIDTH_SHARE} up to {@link #MOST_TAPS} pixels, so near spheres blend
 * as widely as far ones until the cap; a neighbour farther in depth than the
 * sphere's radius times {@link #DEPTH_SHARE} weighs nothing, so the
 * silhouette against the world and between a near and a far stream stays
 * sharp, and a pixel no splat covered keeps the world's depth untouched.
 * Every weight falls smoothly to nothing at its edge, so nothing the kernel
 * gains or loses as it moves steps the average.
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
public final class DrinkBlur {

    /**
     * The most pixels the kernel reaches either side, reading every one: taps spread a stride apart stepped the
     * average once a stride as each crossed a silhouette, contour rings along every edge of the goo.
     */
    public static final int MOST_TAPS = 32;
    /** The kernel's half width on screen, as a share of the sphere's radius there. */
    public static final double WIDTH_SHARE = 1.0;
    /** The depth difference past which a neighbour weighs nothing, as a share of the sphere's radius. */
    public static final double DEPTH_SHARE = 1.0;
    /** The radius a splat's coverage channel reads 1 at: the block's own half width, the widest a splat is. */
    public static final double RADIUS_SCALE = DrinkTree.MOUTH;
    /** The coverage under which a pixel holds no splat: half the coverage channel's least step. */
    public static final double COVERED = 0.002;

    private DrinkBlur() {
    }

    /**
     * @param radius         the sphere's radius at a pixel, in blocks
     * @param pixelsPerBlock how many pixels a block spans on screen at that pixel's depth
     * @return the kernel's half width there, in pixels, from one to {@link #MOST_TAPS}
     */
    public static double widthOf(double radius, double pixelsPerBlock) {
        return Math.clamp(WIDTH_SHARE * radius * pixelsPerBlock, 1, MOST_TAPS);
    }

    /**
     * @param width the kernel's half width, in pixels
     * @return how many pixels it reads either side
     */
    public static int tapsOf(double width) {
        return (int) Math.ceil(width);
    }

    /**
     * A neighbour's weight: falling smoothly to nothing at the kernel's edge,
     * so a pixel the kernel gains as it widens enters weighing nothing, and to
     * nothing at the depth threshold.
     *
     * @param offset     the neighbour's offset as a share of the kernel's half width, -1 to 1
     * @param depthDelta how much farther the neighbour is than the pixel, in blocks
     * @param threshold  the depth difference at which a neighbour weighs nothing, in blocks
     * @return the weight, 1 at the pixel itself
     */
    public static double weightOf(double offset, double depthDelta, double threshold) {
        return falloff(offset) * falloff(depthDelta / threshold);
    }

    private static double falloff(double share) {
        double inside = 1 - share * share;
        return inside <= 0 ? 0 : inside * inside;
    }

    /**
     * One blur pass along a row of pixels, as the shader runs it: each
     * covered pixel's distance averaged over the covered neighbours its
     * kernel reaches; an uncovered pixel kept as it was.
     *
     * @param distance each pixel's distance from the eye, in blocks
     * @param radius   each pixel's sphere radius, in blocks, 0 where no splat covers it
     * @param focal    how many pixels a block spans on screen a block from the eye, so a pixel's own distance sets
     *                 how many it spans there
     * @return the blurred distances
     */
    public static double[] blurRow(double[] distance, double[] radius, double focal) {
        double[] blurred = distance.clone();
        for (int pixel = 0; pixel < distance.length; pixel++) {
            if (radius[pixel] > 0) {
                blurred[pixel] = blurredAt(distance, radius, pixel, focal / distance[pixel]);
            }
        }
        return blurred;
    }

    private static double blurredAt(double[] distance, double[] radius, int pixel, double pixelsPerBlock) {
        double width = widthOf(radius[pixel], pixelsPerBlock);
        double threshold = DEPTH_SHARE * radius[pixel];
        double weights = 0;
        double sum = 0;
        for (int tap = -tapsOf(width); tap <= tapsOf(width); tap++) {
            int at = Math.clamp(pixel + tap, 0, distance.length - 1);
            if (radius[at] > 0) {
                double weight = weightOf(tap / width, distance[at] - distance[pixel], threshold);
                weights += weight;
                sum += weight * distance[at];
            }
        }
        return sum / weights;
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
