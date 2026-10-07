package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.util.Mth;

/**
 * Times a ripple: each ring is born at the center, grows to its edge radius
 * and fades as it grows, gone the moment it meets the edge, with the rings
 * staggered so several show at once (decision ripple-rings-fade-to-face-edge).
 * The bullseye's edge is half a block; a held ghost names its own
 * (decision held-visual-ghosts-the-landing-in-two-passes).
 */
public final class RippleRings {
    /** Rings alive at once, evenly staggered through the period. */
    static final int RING_COUNT = 3;
    /** Real-time seconds one ring takes from the center to its edge. */
    static final double PERIOD_SECONDS = 1.2;
    /** Radius at which a bullseye ring meets the face's edge: half a block from the face center. */
    static final double EDGE_RADIUS = 0.5;

    private RippleRings() {}

    /**
     * The phase of every ring at a moment, each in [0, 1): 0 at birth, 1 at the edge.
     *
     * @param nowSeconds seconds on the real-time clock
     * @return one phase per ring, staggered by an equal share of the period
     */
    public static double[] ringPhases(double nowSeconds) {
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
    public static boolean isAlive(double phase) {
        return phase >= 0 && phase < 1;
    }

    /**
     * A bullseye ring's radius at a phase, growing from zero to the face's edge.
     *
     * @param phase the ring's phase
     * @return the radius in blocks, never past the edge radius
     */
    static double ringRadius(double phase) {
        return ringRadius(phase, EDGE_RADIUS);
    }

    /**
     * A ring's radius at a phase, growing from zero to the edge radius it is scaled to.
     *
     * @param phase      the ring's phase
     * @param edgeRadius the radius the ring meets its edge at, in blocks
     * @return the radius in blocks, never past the edge radius
     */
    public static double ringRadius(double phase, double edgeRadius) {
        return edgeRadius * Mth.clamp(phase, 0, 1);
    }

    /**
     * An inward ring's radius at a phase: born at the edge radius and closing on
     * the center, the outward ring's phase run backward.
     * black-hole-rings-pulse-inward-to-the-pull-radius
     *
     * @param phase      the ring's phase
     * @param edgeRadius the radius the ring is born at, in blocks
     * @return the radius in blocks, zero at the end of its life
     */
    public static double inwardRingRadius(double phase, double edgeRadius) {
        return ringRadius(1 - Mth.clamp(phase, 0, 1), edgeRadius);
    }

    /**
     * An inward ring's opacity at a phase: nothing at birth on the edge,
     * gaining as it closes on the center, the outward ring's phase run backward.
     * black-hole-rings-pulse-inward-to-the-pull-radius
     *
     * @param phase the ring's phase
     * @return the opacity in [0, 1]
     */
    public static double inwardRingOpacity(double phase) {
        return ringOpacity(1 - Mth.clamp(phase, 0, 1));
    }

    /**
     * A ring's opacity at a phase, full at birth and falling to zero as the
     * ring meets the edge.
     *
     * @param phase the ring's phase
     * @return the opacity in [0, 1]
     */
    public static double ringOpacity(double phase) {
        double remaining = 1 - Mth.clamp(phase, 0, 1);
        return remaining * remaining;
    }
}
