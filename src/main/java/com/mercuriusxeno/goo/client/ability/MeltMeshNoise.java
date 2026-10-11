package com.mercuriusxeno.goo.client.ability;

/**
 * A repeatable random share for a seed, which picks where melting goo forms
 * and which of its types each patch is.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class MeltMeshNoise {

    private static final long MULTIPLIER = 6_364_136_223_846_793_005L;
    private static final long INCREMENT = 1_442_695_040_888_963_407L;
    private static final int SHIFT = 33;
    private static final long MASK = 0xFFFF;
    private static final long PRIME_X = 73_856_093L;
    private static final long PRIME_Y = 19_349_663L;
    private static final long PRIME_Z = 83_492_791L;
    private static final double EASE_BASE = 3;
    private static final double EASE_SLOPE = 2;

    private MeltMeshNoise() {
    }

    /**
     * @param seed the seed
     * @return a share from 0 to 1, the same for the same seed
     */
    public static double share(long seed) {
        long mixed = seed * MULTIPLIER + INCREMENT;
        mixed ^= mixed >>> SHIFT;
        return (mixed & MASK) / (double) MASK;
    }

    /**
     * A smooth random field through space: a share from 0 to 1 that drifts
     * gently from point to point, so patches it shapes have soft edges and no
     * seams.
     *
     * @param x    the point's x
     * @param y    the point's y
     * @param z    the point's z
     * @param seed which field
     * @return the field's share at the point
     */
    public static double smooth(double x, double y, double z, long seed) {
        long x0 = (long) Math.floor(x);
        long y0 = (long) Math.floor(y);
        long z0 = (long) Math.floor(z);
        double fx = ease(x - x0);
        double fy = ease(y - y0);
        double fz = ease(z - z0);
        double near = lerp(lerp(corner(x0, y0, z0, seed), corner(x0 + 1, y0, z0, seed), fx),
                lerp(corner(x0, y0 + 1, z0, seed), corner(x0 + 1, y0 + 1, z0, seed), fx), fy);
        double far = lerp(lerp(corner(x0, y0, z0 + 1, seed), corner(x0 + 1, y0, z0 + 1, seed), fx),
                lerp(corner(x0, y0 + 1, z0 + 1, seed), corner(x0 + 1, y0 + 1, z0 + 1, seed), fx), fy);
        return lerp(near, far, fz);
    }

    private static double corner(long x, long y, long z, long seed) {
        return share(((x * PRIME_X) ^ (y * PRIME_Y) ^ (z * PRIME_Z)) + seed);
    }

    private static double ease(double t) {
        return t * t * (EASE_BASE - EASE_SLOPE * t);
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
