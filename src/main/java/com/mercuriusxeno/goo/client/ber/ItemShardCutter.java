package com.mercuriusxeno.goo.client.ber;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Breaks an item's model into shards along its pixels (decision tiles-of-the-items-image):
 * every quad of the model is cut into texel cells in its own plane, seeded points land on
 * opaque cells, and each cell joins the nearest point after its position is warped by 3D
 * noise on the same seed, so shard edges run jagged along texel boundaries on every face of
 * a flat item or a block. The seed comes from the item, so one item always breaks the same way.
 */
final class ItemShardCutter {

    /** The fewest shards an item breaks into. */
    static final int MIN_SHARDS = 8;
    /** The most shards an item breaks into. */
    static final int MAX_SHARDS = 14;
    /** A cell no shard draws, where the item's texture is transparent. */
    static final int NO_SHARD = -1;

    /** How far noise pushes a cell before it picks its nearest point, in texels. */
    private static final double WARP_TEXELS = 1.6;
    /** The texels between the warp noise's lattice points, so edges wander over a few pixels. */
    private static final double WARP_PERIOD_TEXELS = 4.0;
    /** How far a seeded point strays from its cell's center, in texels. */
    private static final double POINT_JITTER = 0.4;
    /** Recuts on a derived seed when a cut leaves fewer than {@link #MIN_SHARDS} shards. */
    private static final int MAX_RECUTS = 16;
    private static final int AXES = 3;
    /** The cubic smoothstep 3t² - 2t³ written as t²(3 - 2t). */
    private static final double SMOOTHSTEP_RISE = 3.0;
    private static final double SMOOTHSTEP_FALL = 2.0;
    /** The width of the span minus one to one, which a number from zero to one stretches over. */
    private static final double SIGNED_SPAN = 2.0;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;
    private static final int SHIFT_A = 30;
    private static final int SHIFT_B = 27;
    private static final int SHIFT_C = 31;
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final int BYTE_MASK = 0xFF;
    private static final int MANTISSA_SHIFT = 11;
    private static final double MANTISSA_SCALE = 0x1.0p-53;
    private static final long[] WARP_SALTS = {0x5851F42D4C957F2DL, 0x14057B7EF767814FL, 0x2127599BF4325C37L};
    private static final long LATTICE_Y_SALT = 0x2545F4914F6CDD1DL;
    private static final long LATTICE_Z_SALT = 0x6C8E9CF570932BD5L;

    private ItemShardCutter() {
    }

    /**
     * Which shard each cell of the model belongs to.
     *
     * @param owners each cell's shard, or {@link #NO_SHARD} for a transparent cell
     * @param count  the shards the model breaks into, numbered from zero
     */
    record Assignment(int[] owners, int count) {
    }

    /**
     * Returns the seed an item breaks by: a stable hash of its registry id, the same on
     * every client and every run.
     *
     * @param itemId the item's registry id, as its string form
     * @return the seed
     */
    static long seedOf(String itemId) {
        long hash = FNV_OFFSET;
        for (byte b : itemId.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (b & BYTE_MASK)) * FNV_PRIME;
        }
        return mix(hash);
    }

    /**
     * Breaks a model's cells into shards.
     *
     * @param seed    the item's seed, as {@link #seedOf} answers
     * @param centers each cell's center, X, Y and Z, in the model's space
     * @param opaque  whether each cell shows the item; points land only on these, and only
     *                these join a shard
     * @param texel   one texel's width in the model's space, which the noise scales by
     * @return each cell's shard
     */
    static Assignment assign(long seed, float[][] centers, boolean[] opaque, float texel) {
        List<Integer> landing = new ArrayList<>();
        for (int i = 0; i < centers.length; i++) {
            if (opaque[i]) {
                landing.add(i);
            }
        }
        Assignment assignment = assignOnce(seed, centers, opaque, texel, landing);
        int fewest = Math.min(MIN_SHARDS, landing.size());
        for (int recut = 1; recut <= MAX_RECUTS && assignment.count() < fewest; recut++) {
            assignment = assignOnce(mix(seed + recut * GOLDEN_GAMMA), centers, opaque, texel, landing);
        }
        return assignment;
    }

    /**
     * One cut: points on distinct opaque cells, every opaque cell to the nearest point from
     * its warped position, shards left empty dropped and the rest numbered in order.
     *
     * @param seed    the seed this cut draws its points and noise from
     * @param centers each cell's center
     * @param opaque  whether each cell shows the item
     * @param texel   one texel's width
     * @param landing the cells a point may land on
     * @return each cell's shard
     */
    private static Assignment assignOnce(long seed, float[][] centers, boolean[] opaque, float texel,
                                         List<Integer> landing) {
        SeedStream stream = new SeedStream(seed);
        int target = MIN_SHARDS + stream.nextInt(MAX_SHARDS - MIN_SHARDS + 1);
        double[][] points = pickPoints(stream, centers, landing, Math.min(target, landing.size()), texel);
        int[] owners = new int[centers.length];
        for (int i = 0; i < centers.length; i++) {
            owners[i] = opaque[i] && points.length > 0 ? nearest(points, warped(seed, centers[i], texel)) : NO_SHARD;
        }
        return compact(owners, points.length);
    }

    private static double[] warped(long seed, float[] center, float texel) {
        double[] at = new double[AXES];
        for (int axis = 0; axis < AXES; axis++) {
            at[axis] = center[axis] + WARP_TEXELS * texel * valueNoise(seed ^ WARP_SALTS[axis], center, texel);
        }
        return at;
    }

    private static double[][] pickPoints(SeedStream stream, float[][] centers, List<Integer> landing, int count,
                                         float texel) {
        List<Integer> pool = new ArrayList<>(landing);
        double[][] points = new double[count][];
        for (int i = 0; i < count; i++) {
            float[] center = centers[pool.remove(stream.nextInt(pool.size()))];
            points[i] = new double[AXES];
            for (int axis = 0; axis < AXES; axis++) {
                points[i][axis] = center[axis] + signed(stream.nextDouble()) * POINT_JITTER * texel;
            }
        }
        return points;
    }

    private static int nearest(double[][] points, double[] at) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            double distance = 0;
            for (int axis = 0; axis < AXES; axis++) {
                double d = points[i][axis] - at[axis];
                distance += d * d;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static Assignment compact(int[] owners, int points) {
        int[] renumbered = new int[points];
        Arrays.fill(renumbered, NO_SHARD);
        int count = 0;
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] == NO_SHARD) {
                continue;
            }
            if (renumbered[owners[i]] == NO_SHARD) {
                renumbered[owners[i]] = count++;
            }
            owners[i] = renumbered[owners[i]];
        }
        return new Assignment(owners, count);
    }

    /**
     * Smooth 3D value noise from minus one to one, its lattice {@link #WARP_PERIOD_TEXELS} apart.
     *
     * @param seed   the noise's seed
     * @param center the point, in the model's space
     * @param texel  one texel's width
     * @return the noise there
     */
    private static double valueNoise(long seed, float[] center, float texel) {
        double period = WARP_PERIOD_TEXELS * texel;
        double gx = center[QuadRectClipper.X] / period;
        double gy = center[QuadRectClipper.Y] / period;
        double gz = center[QuadRectClipper.Z] / period;
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        int z0 = (int) Math.floor(gz);
        double tx = smooth(gx - x0);
        double ty = smooth(gy - y0);
        double tz = smooth(gz - z0);
        double near = lerp(lerp(lattice(seed, x0, y0, z0), lattice(seed, x0 + 1, y0, z0), tx),
                lerp(lattice(seed, x0, y0 + 1, z0), lattice(seed, x0 + 1, y0 + 1, z0), tx), ty);
        double far = lerp(lerp(lattice(seed, x0, y0, z0 + 1), lattice(seed, x0 + 1, y0, z0 + 1), tx),
                lerp(lattice(seed, x0, y0 + 1, z0 + 1), lattice(seed, x0 + 1, y0 + 1, z0 + 1), tx), ty);
        return lerp(near, far, tz);
    }

    private static double lattice(long seed, int x, int y, int z) {
        long hash = mix(seed + x * GOLDEN_GAMMA + y * LATTICE_Y_SALT + z * LATTICE_Z_SALT);
        return signed((hash >>> MANTISSA_SHIFT) * MANTISSA_SCALE);
    }

    private static double smooth(double t) {
        return t * t * (SMOOTHSTEP_RISE - SMOOTHSTEP_FALL * t);
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /**
     * Maps a number from zero to one onto minus one to one.
     *
     * @param unit the number, zero to one
     * @return the signed number
     */
    private static double signed(double unit) {
        return unit * SIGNED_SPAN - 1;
    }

    /**
     * The SplitMix64 finalizer: a stable scramble of every bit of its input.
     *
     * @param value the input
     * @return the scrambled bits
     */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> SHIFT_A)) * MIX_A;
        z = (z ^ (z >>> SHIFT_B)) * MIX_B;
        return z ^ (z >>> SHIFT_C);
    }

    /** A deterministic stream of numbers from one seed, with no per-frame or global state. */
    static final class SeedStream {
        private long state;

        SeedStream(long seed) {
            this.state = seed;
        }

        long nextLong() {
            state += GOLDEN_GAMMA;
            return mix(state);
        }

        int nextInt(int bound) {
            return (int) Math.floorMod(nextLong(), (long) bound);
        }

        double nextDouble() {
            return (nextLong() >>> MANTISSA_SHIFT) * MANTISSA_SCALE;
        }
    }
}
