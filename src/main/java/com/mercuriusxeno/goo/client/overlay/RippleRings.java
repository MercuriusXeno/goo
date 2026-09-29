package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.util.Mth;

/**
 * Times the bullseye's ripple: each ring is born at the face center, grows to
 * the face's edge and fades as it grows, gone the moment it meets the edge,
 * with the rings staggered so several show at once (decision
 * ripple-rings-fade-to-face-edge).
 */
final class RippleRings {
    /** Rings alive at once, evenly staggered through the period. */
    static final int RING_COUNT = 3;
    /** Real-time seconds one ring takes from the face center to the face's edge. */
    static final double PERIOD_SECONDS = 1.2;
    /** Radius at which a ring meets the face's edge: half a block from the face center. */
    static final double EDGE_RADIUS = 0.5;

    private RippleRings() {}

    /**
     * The phase of every ring at a moment, each in [0, 1): 0 at birth, 1 at the edge.
     *
     * @param nowSeconds seconds on the real-time clock
     * @return one phase per ring, staggered by an equal share of the period
     */
    static double[] ringPhases(double nowSeconds) {
        double[] phases = new double[RING_COUNT];
        double base = nowSeconds / PERIOD_SECONDS;
        for (int i = 0; i < RING_COUNT; i++) {
            phases[i] = Mth.frac(base + (double) i / RING_COUNT);
        }
        return phases;
    }

    /**
     * Whether a ring at this phase draws: from birth until it meets the edge.
     *
     * @param phase the ring's phase
     * @return true while the phase lies in [0, 1)
     */
    static boolean isAlive(double phase) {
        return phase >= 0 && phase < 1;
    }

    /**
     * A ring's radius at a phase, growing from zero to the edge radius.
     *
     * @param phase the ring's phase
     * @return the radius in blocks, never past the edge radius
     */
    static double ringRadius(double phase) {
        return EDGE_RADIUS * Mth.clamp(phase, 0, 1);
    }

    /**
     * A ring's opacity at a phase, full at birth and falling to zero as the
     * ring meets the edge.
     *
     * @param phase the ring's phase
     * @return the opacity in [0, 1]
     */
    static double ringOpacity(double phase) {
        double remaining = 1 - Mth.clamp(phase, 0, 1);
        return remaining * remaining;
    }
}
