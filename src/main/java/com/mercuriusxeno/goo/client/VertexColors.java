package com.mercuriusxeno.goo.client;

import net.minecraft.util.ARGB;

/**
 * Color arithmetic the vertex emitters share, written once (decision
 * render-context-is-the-one-emitter).
 */
public final class VertexColors {

    /** The largest alpha channel value. */
    private static final int MAX_ALPHA = 255;

    private VertexColors() {}

    /**
     * Scales the alpha channel of an ARGB color, keeping its RGB channels.
     *
     * @param argb   the source ARGB color
     * @param factor the alpha scale factor, clamped into a valid channel
     * @return the color with its alpha scaled
     */
    public static int scaleAlpha(int argb, float factor) {
        int alpha = Math.clamp((int) (ARGB.alpha(argb) * factor), 0, MAX_ALPHA);
        return ARGB.color(alpha, argb);
    }
}
