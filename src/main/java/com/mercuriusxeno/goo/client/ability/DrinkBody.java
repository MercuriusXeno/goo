package com.mercuriusxeno.goo.client.ability;

/**
 * How a block becomes its stream under an Unmake drink, as the width of the
 * stream's own skin through the block's span: the block is a cube of square
 * rings where it stood, its hard edges softening into a rounded blob over
 * the first part of the drain as it destabilises, shrinking smoothly about
 * its middle as its matter leaves, its surface wobbling once liquid; from its
 * middle a funnel narrows from the blob's width to the stream's over
 * {@link #FUNNEL} blocks, so the stream is wide where it leaves the block and
 * thins as the block empties, one skin with no seam, anchored where the block
 * stood, liquid from the first frame.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkBody {

    /** Blocks past the block's middle over which its matter narrows from the blob's width to the stream's. */
    public static final double FUNNEL = 2.5;
    /** The cube's half width, in blocks. */
    static final double MOUTH = 0.5;
    /** The block's middle along its path, in blocks from its far side. */
    static final double CENTER = DrinkStream.BLOCK_SPAN / 2;
    /** The share of the drain over which the cube's edges soften into a liquid blob. */
    static final double LIQUEFY = 0.35;
    /** The blob's cap exponent while it is still the cube: near flat ends and sharp corners. */
    static final double BOX = 10;
    /** The blob's cap exponent once liquid: a sphere's. */
    static final double ROUND = 2;
    /** How far the liquid blob's surface wobbles, as a share of its width. */
    static final double WOBBLE = 0.06;
    /** Noise cells along a block of the blob for its wobble. */
    private static final double WOBBLE_SCALE = 3;
    /** How fast the wobble churns, in noise cells a tick. */
    private static final double WOBBLE_RATE = 0.04;
    private static final double TWO = 2;
    private static final double THREE = 3;
    private static final long WOBBLE_SALT = 0x5A82_7999L;

    private DrinkBody() {
    }

    /**
     * @param progress how far the drain has gone, 0 to 1
     * @return how big the block's blob still is, 1 the whole cube to 0 gone, shrinking smoothly from the first frame
     */
    public static double sizeAt(double progress) {
        return 1 - ease(Math.clamp(progress, 0, 1));
    }

    /**
     * @param progress how far the drain has gone, 0 to 1
     * @return how liquid the block has become, 0 the hard cube to 1 a blob, over the first {@link #LIQUEFY} of the drain
     */
    public static double liquidityAt(double progress) {
        return ease(Math.clamp(progress / LIQUEFY, 0, 1));
    }

    /**
     * The blob's half width at a distance along the path: the cube, its ends
     * flat and its corners sharp, rounding into a sphere as it liquefies, and
     * shrinking about its middle.
     *
     * @param distance blocks from the block's far side
     * @param progress how far the drain has gone, 0 to 1
     * @return the half width there, 0 outside the blob
     */
    static double blobAt(double distance, double progress) {
        double size = sizeAt(progress);
        double half = CENTER * size;
        if (half <= 0) {
            return 0;
        }
        double t = Math.abs(distance - CENTER) / half;
        if (t >= 1) {
            return 0;
        }
        double exponent = BOX + (ROUND - BOX) * liquidityAt(progress);
        return MOUTH * size * Math.pow(1 - Math.pow(t, exponent), 1 / exponent);
    }

    /**
     * The funnel's half width at a distance along the path: from the blob's
     * width at its middle down to the stream's over {@link #FUNNEL} blocks,
     * nothing behind the middle.
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
     * The half width of a block's matter at a distance along its path: the
     * blob or the funnel, whichever is wider, wobbling once liquid, the wobble
     * fading out along the funnel so the stream keeps its own undulation.
     *
     * @param distance blocks from the block's far side
     * @param progress how far the drain has gone, 0 to 1
     * @param stream   the stream's own radius there
     * @param seed     the block's seed
     * @param now      the game time, with the partial tick
     * @return the half width there, in blocks
     */
    public static double widthAt(double distance, double progress, double stream, long seed, double now) {
        double width = Math.max(blobAt(distance, progress), funnelAt(distance, progress, stream));
        double wobble = TWO * MeltMeshNoise.smooth(distance * WOBBLE_SCALE, now * WOBBLE_RATE, 0, seed + WOBBLE_SALT)
                - 1;
        double blobby = 1 - ease(Math.clamp((distance - CENTER) / FUNNEL, 0, 1));
        return width * (1 + WOBBLE * liquidityAt(progress) * blobby * wobble);
    }

    /**
     * @param distance blocks from the block's far side
     * @param progress how far the drain has gone, 0 to 1
     * @return how round the matter is there, 0 the cube's square to 1 a circle: by how liquid the block is, and
     *         along the funnel in any case
     */
    public static double roundnessAt(double distance, double progress) {
        double funnel = ease(Math.clamp((distance - CENTER) / FUNNEL, 0, 1));
        return 1 - (1 - liquidityAt(progress)) * (1 - funnel);
    }

    private static double ease(double t) {
        return t * t * (THREE - TWO * t);
    }
}
