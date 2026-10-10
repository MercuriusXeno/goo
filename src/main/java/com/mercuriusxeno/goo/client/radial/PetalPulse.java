package com.mercuriusxeno.goo.client.radial;

/**
 * The brightness a wheel petal pulses at while the player holds its effect:
 * swinging on game time between the resting and the hovered opacity, twice
 * a period, so a held effect reads on the wheel; an inactive petal rests.
 * wheel-pulses-the-active-effect
 */
final class PetalPulse {

    /** The ticks one pulse cycle spans: two seconds. */
    static final float PERIOD_TICKS = 40f;
    private static final double FULL_TURN = 2 * Math.PI;

    private PetalPulse() {
    }

    /**
     * The opacity a petal draws at.
     *
     * @param active   whether the player holds the petal's effect
     * @param gameTime the game time, fraction included
     * @return the resting opacity for an inactive petal, a pulse between resting and hovered for an active one
     */
    static int alpha(boolean active, float gameTime) {
        if (!active) {
            return RadialWheelRenderer.NORMAL_ALPHA;
        }
        double swing = Math.abs(Math.sin(FULL_TURN * gameTime / PERIOD_TICKS));
        return RadialWheelRenderer.NORMAL_ALPHA
                + (int) Math.round(swing * (RadialWheelRenderer.HOVER_ALPHA - RadialWheelRenderer.NORMAL_ALPHA));
    }
}
