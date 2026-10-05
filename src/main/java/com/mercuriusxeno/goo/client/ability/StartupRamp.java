package com.mercuriusxeno.goo.client.ability;

/**
 * The startup frames a nether hole draws over its gather's last ticks,
 * before its expand begins (decision dome-fades-in-before-its-start): the
 * hole starts at nothing and transparent, grows on an ease-in and fades in,
 * and meets expand's first frame at its radius and full opacity.
 */
public final class StartupRamp {

    /** Ticks the ramp runs before the phase it leads into. */
    public static final int RAMP_TICKS = 3;
    /** Opacity in the vertex color's alpha byte. */
    private static final int FULL_ALPHA = 0xFF;

    private StartupRamp() {
    }

    /**
     * The radius through the ramp: a cubic ease-in from nothing to the radius
     * the ramp meets.
     *
     * @param ramp         the ramp's share in [0, 1]
     * @param targetRadius the radius the ramp ends on
     * @return the radius in blocks
     */
    public static float radius(float ramp, float targetRadius) {
        return targetRadius * ramp * ramp * ramp;
    }

    /**
     * The opacity through the ramp, as the vertex color's alpha byte:
     * nothing at the start, full at its end.
     *
     * @param ramp the ramp's share in [0, 1]
     * @return the alpha byte in [0, 255]
     */
    public static int alpha(float ramp) {
        return Math.round(Math.min(1f, Math.max(0f, ramp)) * FULL_ALPHA);
    }
}
