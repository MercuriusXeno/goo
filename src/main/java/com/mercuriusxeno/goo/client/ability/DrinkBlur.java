package com.mercuriusxeno.goo.client.ability;

/**
 * The blur that makes the drink's splatted spheres one skin, and the normal
 * the shade pass reads off it, stated in Java: drink_blur.fsh runs the same
 * kernel and drink_shade.fsh the same normal, and DrinkShaderTest holds their
 * constants to these. One pass averages each covered pixel's distance from
 * the eye and its color with every covered pixel within a disc about it, the
 * disc as wide on screen as the sphere there is times {@link #WIDTH_SHARE} up
 * to {@link #MOST_REACH} pixels, so near spheres blend as widely as far ones
 * until the cap; a neighbour farther in depth than the sphere's radius times
 * {@link #DEPTH_SHARE} weighs nothing, so the silhouette against the world
 * and between a near and a far stream stays sharp, and a pixel no splat
 * covered keeps the world's depth untouched. Every weight falls smoothly to
 * nothing at its edge, so nothing the disc gains or loses as it moves steps
 * the average. A blur run across the screen and then down it averaged what
 * the first pass had already pulled unevenly at a rim, one column differently
 * from the next, and left lines hanging down the goo; the disc reads both
 * axes at once and leaves none.
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
public final class DrinkBlur {

    /** The most pixels the disc reaches from its middle, every one within it read. */
    public static final int MOST_REACH = 12;
    /** The disc's radius on screen, as a share of the sphere's radius there. */
    public static final double WIDTH_SHARE = 1.0;
    /** The depth difference past which a neighbour weighs nothing, as a share of the sphere's radius. */
    public static final double DEPTH_SHARE = 1.0;
    /** The radius a splat's coverage channel reads 1 at: the block's own half width, the widest a splat is. */
    public static final double RADIUS_SCALE = DrinkTree.MOUTH;
    /** The coverage under which a pixel holds no splat: half the coverage channel's least step. */
    public static final double COVERED = 0.002;
    /** The sums a disc gathers: its weights, then its weighted distances. */
    private static final int SUMS = 2;

    /**
     * A pixel and one row of its disc.
     *
     * @param row    the pixel's row
     * @param column the pixel's column
     * @param down   the disc row's offset down from the pixel
     */
    private record Place(int row, int column, int down) {
    }

    private DrinkBlur() {
    }

    /**
     * @param radius         the sphere's radius at a pixel, in blocks
     * @param pixelsPerBlock how many pixels a block spans on screen at that pixel's depth
     * @return the disc's radius there, in pixels, from one to {@link #MOST_REACH}
     */
    public static double widthOf(double radius, double pixelsPerBlock) {
        return Math.clamp(WIDTH_SHARE * radius * pixelsPerBlock, 1, MOST_REACH);
    }

    /**
     * A neighbour's weight: falling smoothly to nothing at the disc's edge,
     * so a pixel the disc gains as it widens enters weighing nothing, and to
     * nothing at the depth threshold.
     *
     * @param offset     the neighbour's distance from the pixel as a share of the disc's radius
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
     * The blur over an image, as the shader runs it: each covered pixel's
     * distance averaged over the covered pixels within its disc; an
     * uncovered pixel kept as it was.
     *
     * @param distance each pixel's distance from the eye, in blocks, by row then column
     * @param radius   each pixel's sphere radius, in blocks, 0 where no splat covers it, by row then column
     * @param focal    how many pixels a block spans on screen a block from the eye, so a pixel's own distance sets
     *                 how many it spans there
     * @return the blurred distances, by row then column
     */
    public static double[][] blurImage(double[][] distance, double[][] radius, double focal) {
        double[][] blurred = new double[distance.length][];
        for (int row = 0; row < distance.length; row++) {
            blurred[row] = distance[row].clone();
            for (int column = 0; column < distance[row].length; column++) {
                if (radius[row][column] > 0) {
                    blurred[row][column] = blurredAt(distance, radius, row, column,
                            focal / distance[row][column]);
                }
            }
        }
        return blurred;
    }

    private static double blurredAt(double[][] distance, double[][] radius, int row, int column,
                                    double pixelsPerBlock) {
        double width = widthOf(radius[row][column], pixelsPerBlock);
        int reach = (int) Math.ceil(width);
        double[] sums = new double[SUMS];
        for (int down = Math.max(-reach, -row); down <= reach && row + down < distance.length; down++) {
            gatherRow(distance, radius, new Place(row, column, down), width, sums);
        }
        return sums[1] / sums[0];
    }

    /**
     * Adds one row of a pixel's disc: each covered pixel weighed by its
     * distance from the pixel and its depth difference.
     *
     * @param distance each pixel's distance from the eye
     * @param radius   each pixel's sphere radius, 0 where uncovered
     * @param place    the pixel and the disc row's offset down from it
     * @param width    the disc's radius, in pixels
     * @param sums     the weights then the weighted distances, added to
     */
    private static void gatherRow(double[][] distance, double[][] radius, Place place, double width,
                                  double[] sums) {
        int row = place.row();
        int column = place.column();
        int down = place.down();
        double here = distance[row][column];
        double threshold = DEPTH_SHARE * radius[row][column];
        int reach = (int) Math.ceil(width);
        double[] line = distance[row + down];
        for (int across = Math.max(-reach, -column); across <= reach && column + across < line.length; across++) {
            if (radius[row + down][column + across] > 0) {
                double weight = weightOf(Math.sqrt(down * down + across * across) / width,
                        line[column + across] - here, threshold);
                sums[0] += weight;
                sums[1] += weight * line[column + across];
            }
        }
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
