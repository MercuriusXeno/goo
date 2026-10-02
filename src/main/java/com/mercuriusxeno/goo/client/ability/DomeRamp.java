package com.mercuriusxeno.goo.client.ability;

import java.util.OptionalDouble;

/**
 * The startup frames a burnout dome draws over the fuse's last ticks, before
 * the burnout itself arrives (decision dome-fades-in-before-its-start): the
 * dome starts at nothing and transparent, grows on an ease-in and fades in,
 * and meets the burnout at the radius and opacity of its first drawn frame,
 * so the burnout plays on time with no jump.
 */
public final class DomeRamp {

    /**
     * Ticks the ramp runs before detonation: a short run inside the orb's
     * jitter window, so the dome fades in as the orb shakes.
     */
    public static final int RAMP_TICKS = 3;
    /** Opacity in the vertex color's alpha byte. */
    private static final int FULL_ALPHA = 0xFF;

    private DomeRamp() {
    }

    /**
     * How far the ramp has run for a marker in its fuse.
     *
     * @param fuseRemaining  the fuse ticks remaining, below zero for a fuse held on a trigger
     * @param partialTick    the partial tick
     * @param playsBurnout   true when the marker's ability plays a burnout explosion
     * @param behaviorActive true once the marker's program runs, the fuse spent
     * @return the ramp's share in [0, 1], 0 at its start and 1 at detonation, or
     *         empty when the marker draws no ramp
     */
    public static OptionalDouble rampAt(int fuseRemaining, float partialTick, boolean playsBurnout,
                                        boolean behaviorActive) {
        if (!playsBurnout || behaviorActive) {
            return OptionalDouble.empty();
        }
        if (fuseRemaining < 0) {
            return OptionalDouble.of(0);
        }
        float smoothFuse = Math.max(0f, fuseRemaining - partialTick);
        if (smoothFuse >= RAMP_TICKS) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(1f - smoothFuse / RAMP_TICKS);
    }

    /**
     * The burnout's progress on its first drawn frame: one tick in, since the
     * client's level ticks once between landing the burnout and drawing it.
     *
     * @param durationTicks the burnout's duration in ticks
     * @return the progress in [0, 1]
     */
    public static float firstDrawnProgress(int durationTicks) {
        return durationTicks <= 0 ? 1f : Math.min(1f, 1f / durationTicks);
    }

    /**
     * The dome's radius through the ramp: a cubic ease-in from nothing to the
     * first drawn frame's radius, so it ends near the pace the burnout's
     * ease-out growth opens at.
     *
     * @param ramp             the ramp's share in [0, 1]
     * @param firstFrameRadius the radius of the burnout's first drawn frame
     * @return the radius in blocks
     */
    public static float radius(float ramp, float firstFrameRadius) {
        return firstFrameRadius * ramp * ramp * ramp;
    }

    /**
     * The dome's opacity through the ramp, as the vertex color's alpha byte:
     * nothing at the start, full at detonation.
     *
     * @param ramp the ramp's share in [0, 1]
     * @return the alpha byte in [0, 255]
     */
    public static int alpha(float ramp) {
        return Math.round(Math.min(1f, Math.max(0f, ramp)) * FULL_ALPHA);
    }
}
