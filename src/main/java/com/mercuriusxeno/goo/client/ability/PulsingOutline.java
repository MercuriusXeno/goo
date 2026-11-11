package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The pulsing glow outline a marked mob wears: vanilla's glow outline in a
 * mark's color, its brightness swelling and fading. Rally draws it in red and
 * the outlined ailments in their own colors.
 * rallied-mobs-wear-a-red-clenched-fist
 * ailment-glisten-becomes-a-pulsing-outline
 */
public final class PulsingOutline {

    /** Ticks per pulse, one and a half seconds. */
    static final float PULSE_PERIOD = 30f;
    /** The share of each channel the outline keeps at the pulse's dimmest. */
    private static final float DIM_SHARE = 0x70 / 255f;
    private static final float HALF = 0.5f;
    private static final int OPAQUE = 0xFF;

    private PulsingOutline() {
    }

    /**
     * The outline's color at a game time: the mark's color whole at the start
     * of each pulse, dimmest halfway through it, always opaque.
     *
     * @param rgb      the mark's color, 0xRRGGBB
     * @param gameTime the game time including the partial tick
     * @return the ARGB color
     */
    public static int color(int rgb, float gameTime) {
        float brightness = HALF + HALF * Mth.cos(Mth.TWO_PI * gameTime / PULSE_PERIOD);
        float share = Mth.lerp(brightness, DIM_SHARE, 1f);
        return ARGB.color(OPAQUE, Math.round(ARGB.red(rgb) * share), Math.round(ARGB.green(rgb) * share),
                Math.round(ARGB.blue(rgb) * share));
    }
}
