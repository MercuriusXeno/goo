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
}
