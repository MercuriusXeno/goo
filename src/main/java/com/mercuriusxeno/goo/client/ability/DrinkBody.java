package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;

/**
 * How a block becomes its stream under an Unmake drink: the block is a box
 * of liquid where it stood, its hard edges rounding into a sphere over the
 * first part of the drain as it destabilises and shrinking smoothly about
 * its middle as its matter leaves; from its middle a funnel of stream
 * narrows from the box's width to the stream's over {@link #FUNNEL} blocks,
 * so the stream is wide where it leaves the block and thins as the block
 * empties, the two one surface in the drink's field, liquid from the first
 * frame, anchored where the block stood.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkBody {

    /** Blocks past the block's middle over which its matter narrows from the box's width to the stream's. */
    public static final double FUNNEL = 2.5;
    /** The cube's half width, in blocks. */
    static final double MOUTH = 0.5;
    /** The block's middle along its path, in blocks from its far side. */
    static final double CENTER = DrinkStream.BLOCK_SPAN / 2;
    /** The share of the drain over which the cube's edges round into a liquid sphere. */
    static final double LIQUEFY = 0.35;
    /** How far the liquid's surface wobbles, as a share of its width. */
    static final double WOBBLE = 0.06;
    /** Noise cells along a block of the liquid for its wobble. */
    private static final double WOBBLE_SCALE = 3;
    /** How fast the wobble churns, in noise cells a tick. */
    private static final double WOBBLE_RATE = 0.04;
    private static final double TWO = 2;
    private static final double THREE = 3;
    private static final long WOBBLE_SALT = 0x5A82_7999L;

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

    /**
     * The funnel's half width at a distance along the path: from the box's
     * width at the block's middle down to the stream's over {@link #FUNNEL}
     * blocks, nothing behind the middle.
     *
     * @param distance blocks from the block's far side
     * @param progress how far the drain has gone, 0 to 1
     * @param stream   the stream's own radius there
     * @return the half width there
     */
    static double funnelAt(double distance, double progress, double stream) {
        if (distance < CENTER) {
            return 0;
        }
        double mouth = MOUTH * sizeAt(progress);
        return mouth + (stream - mouth) * ease(Math.clamp((distance - CENTER) / FUNNEL, 0, 1));
    }

    /**
     * The radius of a block's stream at a distance along its path: the funnel
     * from the block's middle, wobbling where it is still blob, the wobble
     * fading out along the funnel so the stream keeps its own undulation.
     *
     * @param distance blocks from the block's far side
     * @param progress how far the drain has gone, 0 to 1
     * @param stream   the stream's own radius there
     * @param seed     the block's seed
     * @param now      the game time, with the partial tick
     * @return the radius there, in blocks
     */
    public static double widthAt(double distance, double progress, double stream, long seed, double now) {
        double width = funnelAt(distance, progress, stream);
        double wobble = TWO * MeltMeshNoise.smooth(distance * WOBBLE_SCALE, now * WOBBLE_RATE, 0, seed + WOBBLE_SALT)
                - 1;
        double blobby = 1 - ease(Math.clamp((distance - CENTER) / FUNNEL, 0, 1));
        return width * (1 + WOBBLE * liquidityAt(progress) * blobby * wobble);
    }

    private static double ease(double t) {
        return t * t * (THREE - TWO * t);
    }
}
