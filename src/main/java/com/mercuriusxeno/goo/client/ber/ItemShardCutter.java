package com.mercuriusxeno.goo.client.ber;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Breaks an item's flat face into shards along its pixels (decision tiles-of-the-items-image):
 * seeded points land on the face's opaque texels, and each texel joins the nearest point
 * after its position is warped by noise on the same seed, so shard edges run jagged along
 * texel boundaries. The seed comes from the item, so one item always breaks the same way.
 */
final class ItemShardCutter {

    /** The fewest shards an item breaks into. */
    static final int MIN_SHARDS = 8;
    /** The most shards an item breaks into. */
    static final int MAX_SHARDS = 14;

    /** How far noise pushes a texel before it picks its nearest point, in texels. */
    private static final double WARP_TEXELS = 1.6;
    /** The texels between the warp noise's lattice points, so edges wander over a few pixels. */
    private static final double WARP_PERIOD_TEXELS = 4.0;
    /** How far a seeded point strays from its texel's center, in texels. */
    private static final double POINT_JITTER = 0.4;
    /** Recuts on a derived seed when a cut leaves fewer than {@link #MIN_SHARDS} shards. */
    private static final int MAX_RECUTS = 16;
    private static final double HALF = 0.5;
    /** The cubic smoothstep 3t² - 2t³ written as t²(3 - 2t). */
    private static final double SMOOTHSTEP_RISE = 3.0;
    private static final double SMOOTHSTEP_FALL = 2.0;
    /** The width of the span minus one to one, which a number from zero to one stretches over. */
    private static final double SIGNED_SPAN = 2.0;
    /** A point no texel has claimed yet, while the shards are numbered. */
    private static final int UNNUMBERED = -1;
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
    private static final long WARP_X_SALT = 0x5851F42D4C957F2DL;
    private static final long WARP_Z_SALT = 0x14057B7EF767814FL;
    private static final long LATTICE_ROW_SALT = 0x2545F4914F6CDD1DL;

    private ItemShardCutter() {
    }

    /**
     * Whether a texel of the face shows any of the item.
     */
    @FunctionalInterface
    interface TexelOpacity {
        /** Every texel counts as opaque. */
        TexelOpacity ALL = (column, row) -> true;

        /**
         * @param column the texel's column, along the face's X
         * @param row    the texel's row, along the face's Y
         * @return true if the texel is not fully transparent
         */
        boolean opaque(int column, int row);
    }

    /**
     * One horizontal run of a shard's texels in one row.
     *
     * @param row        the row, along the face's Y
     * @param fromColumn the first column of the run
     * @param toColumn   the column past the run's last
     */
    record TexelRun(int row, int fromColumn, int toColumn) {
    }

    /**
     * Which shard each texel of the face belongs to.
     *
     * @param columns the texels along the face's X
     * @param rows    the texels along the face's Y
     * @param owners  each texel's shard, row by row from row zero
     * @param count   the shards the face breaks into, numbered from zero
     */
    record ShardMap(int columns, int rows, int[] owners, int count) {

        /**
         * A face left whole, one shard owning every texel.
         *
         * @param columns the texels along the face's X
         * @param rows    the texels along the face's Y
         * @return the one-shard map
         */
        static ShardMap whole(int columns, int rows) {
            return new ShardMap(columns, rows, new int[columns * rows], 1);
        }

        /**
         * @param column the texel's column
         * @param row    the texel's row
         * @return the shard that texel belongs to
         */
        int ownerOf(int column, int row) {
            return owners[row * columns + column];
        }

        /**
         * Returns a shard's texels as runs along each row, the quads the shard's face draws.
         *
         * @param shard the shard
         * @return its runs, row by row
         */
        List<TexelRun> runs(int shard) {
            List<TexelRun> runs = new ArrayList<>();
            for (int row = 0; row < rows; row++) {
                int column = 0;
                while (column < columns) {
                    if (ownerOf(column, row) != shard) {
                        column++;
                        continue;
                    }
                    int from = column;
                    while (column < columns && ownerOf(column, row) == shard) {
                        column++;
                    }
                    runs.add(new TexelRun(row, from, column));
                }
            }
            return runs;
        }

        /**
         * Returns a shard's centroid over its texel centers, in texels from the face's low corner.
         *
         * @param shard the shard
         * @return the centroid's column and row coordinates
         */
        double[] centroid(int shard) {
            double sumColumn = 0;
            double sumRow = 0;
            int texels = 0;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    if (ownerOf(column, row) == shard) {
                        sumColumn += column + HALF;
                        sumRow += row + HALF;
                        texels++;
                    }
                }
            }
            return new double[] {sumColumn / Math.max(texels, 1), sumRow / Math.max(texels, 1)};
        }
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
     * Breaks a face into shards.
     *
     * @param seed    the item's seed, as {@link #seedOf} answers
     * @param columns the texels along the face's X
     * @param rows    the texels along the face's Y
     * @param opacity which texels show the item, where the points land
     * @return the shard map
     */
    static ShardMap cut(long seed, int columns, int rows, TexelOpacity opacity) {
        List<int[]> landing = opaqueTexels(columns, rows, opacity);
        if (landing.isEmpty()) {
            landing = opaqueTexels(columns, rows, TexelOpacity.ALL);
        }
        ShardMap map = cutOnce(seed, columns, rows, landing);
        for (int recut = 1; recut <= MAX_RECUTS && map.count() < Math.min(MIN_SHARDS, landing.size()); recut++) {
            map = cutOnce(mix(seed + recut * GOLDEN_GAMMA), columns, rows, landing);
        }
        return map;
    }

    private static List<int[]> opaqueTexels(int columns, int rows, TexelOpacity opacity) {
        List<int[]> texels = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (opacity.opaque(column, row)) {
                    texels.add(new int[] {column, row});
                }
            }
        }
        return texels;
    }

    /**
     * One cut: points on distinct landing texels, every texel to the nearest point from its
     * warped position, shards left empty dropped and the rest numbered in order.
     *
     * @param seed    the seed this cut draws its points and noise from
     * @param columns the texels along the face's X
     * @param rows    the texels along the face's Y
     * @param landing the texels a point may land on
     * @return the shard map
     */
    private static ShardMap cutOnce(long seed, int columns, int rows, List<int[]> landing) {
        SeedStream stream = new SeedStream(seed);
        int target = MIN_SHARDS + stream.nextInt(MAX_SHARDS - MIN_SHARDS + 1);
        double[][] points = pickPoints(stream, landing, Math.min(target, landing.size()));
        int[] owners = new int[columns * rows];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double x = column + HALF;
                double y = row + HALF;
                double warpedX = x + WARP_TEXELS * valueNoise(seed ^ WARP_X_SALT, x, y);
                double warpedY = y + WARP_TEXELS * valueNoise(seed ^ WARP_Z_SALT, x, y);
                owners[row * columns + column] = nearest(points, warpedX, warpedY);
            }
        }
        return compact(columns, rows, owners, points.length);
    }

    private static double[][] pickPoints(SeedStream stream, List<int[]> landing, int count) {
        List<int[]> pool = new ArrayList<>(landing);
        double[][] points = new double[count][];
        for (int i = 0; i < count; i++) {
            int[] texel = pool.remove(stream.nextInt(pool.size()));
            points[i] = new double[] {
                texel[0] + HALF + signed(stream.nextDouble()) * POINT_JITTER,
                texel[1] + HALF + signed(stream.nextDouble()) * POINT_JITTER,
            };
        }
        return points;
    }

    private static int nearest(double[][] points, double x, double y) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            double dx = points[i][0] - x;
            double dy = points[i][1] - y;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static ShardMap compact(int columns, int rows, int[] owners, int points) {
        int[] renumbered = new int[points];
        Arrays.fill(renumbered, UNNUMBERED);
        int count = 0;
        for (int i = 0; i < owners.length; i++) {
            if (renumbered[owners[i]] < 0) {
                renumbered[owners[i]] = count++;
            }
            owners[i] = renumbered[owners[i]];
        }
        return new ShardMap(columns, rows, owners, count);
    }

    /**
     * Smooth value noise from minus one to one, its lattice {@link #WARP_PERIOD_TEXELS} apart.
     *
     * @param seed the noise's seed
     * @param x    the X, in texels
     * @param y    the Y, in texels
     * @return the noise there
     */
    private static double valueNoise(long seed, double x, double y) {
        double gx = x / WARP_PERIOD_TEXELS;
        double gy = y / WARP_PERIOD_TEXELS;
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        double tx = smooth(gx - x0);
        double ty = smooth(gy - y0);
        double low = lerp(lattice(seed, x0, y0), lattice(seed, x0 + 1, y0), tx);
        double high = lerp(lattice(seed, x0, y0 + 1), lattice(seed, x0 + 1, y0 + 1), tx);
        return lerp(low, high, ty);
    }

    private static double lattice(long seed, int x, int y) {
        long hash = mix(seed + x * GOLDEN_GAMMA + y * LATTICE_ROW_SALT);
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
    private static final class SeedStream {
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
