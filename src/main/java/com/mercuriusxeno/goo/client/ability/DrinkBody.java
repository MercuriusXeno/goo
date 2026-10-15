package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;

/**
 * How a block becomes its stream under an Unmake drink: the block is a box
 * of liquid where it stood, its hard edges rounding into a sphere over the
 * first part of the drain as it destabilises and shrinking smoothly about
 * its middle as its matter leaves; its stream's spout leaves its middle as
 * wide as the box, so the stream is widest where it leaves the block and
 * thins as the block empties, the two one surface in the drink's field,
 * liquid from the first frame, anchored where the block stood.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision unmake-stream-is-a-shrinking-waterspout
 */
public final class DrinkBody {

    /** The cube's half width, in blocks. */
    static final double MOUTH = 0.5;
    /** The block's middle along its path, in blocks from its far side. */
    static final double CENTER = DrinkStream.BLOCK_SPAN / 2;
    /** The share of the drain over which the cube's edges round into a liquid sphere. */
    static final double LIQUEFY = 0.35;
    private static final double TWO = 2;
    private static final double THREE = 3;

    private DrinkBody() {
    }

    /**
     * The block's box of liquid: a rounded box about its middle, aligned to
     * the world as the block was, so it never twists with the stream.
     *
     * @param center   the box's middle
     * @param half     its half width, in blocks
     * @param rounding how far its edges and corners are rounded, in blocks, its half width for a sphere
     */
    public record Box(Vec3 center, double half, double rounding) {

        /**
         * @param point a point in the world
         * @return the signed distance from the point to the box's surface, below zero inside
         */
        public double signedDistance(Vec3 point) {
            double core = half - rounding;
            double qx = Math.abs(point.x - center.x) - core;
            double qy = Math.abs(point.y - center.y) - core;
            double qz = Math.abs(point.z - center.z) - core;
            double outside = Math.sqrt(square(Math.max(qx, 0)) + square(Math.max(qy, 0)) + square(Math.max(qz, 0)));
            double inside = Math.min(Math.max(qx, Math.max(qy, qz)), 0);
            return outside + inside - rounding;
        }

        private static double square(double value) {
            return value * value;
        }
    }

    /**
     * @param progress how far the drain has gone, 0 to 1
     * @return how big the block's box still is, 1 the whole cube to 0 gone, shrinking smoothly from the first frame
     */
    public static double sizeAt(double progress) {
        return 1 - ease(Math.clamp(progress, 0, 1));
    }

    /**
     * @param progress how far the drain has gone, 0 to 1
     * @return how liquid the block has become, 0 the hard cube to 1 a sphere, over the first {@link #LIQUEFY} of the
     *         drain
     */
    public static double liquidityAt(double progress) {
        return ease(Math.clamp(progress / LIQUEFY, 0, 1));
    }

    /**
     * @param center   the block's middle
     * @param progress how far the drain has gone, 0 to 1
     * @return the block's box of liquid now, or null once it is gone
     */
    public static Box boxAt(Vec3 center, double progress) {
        double half = MOUTH * sizeAt(progress);
        return new Box(center, half, half * liquidityAt(progress));
    }

    private static double ease(double t) {
        return t * t * (THREE - TWO * t);
    }
}
