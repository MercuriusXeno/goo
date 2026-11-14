package com.mercuriusxeno.goo.client.ability;

/**
 * Packs a 0..1 value into a color channel byte, the way the burnout and
 * ability shaders read their progress, opacity and coordinates from vertex color.
 */
public final class ColorBytes {

    /** The largest value a color channel byte holds. */
    private static final int BYTE_MAX = 255;

    private ColorBytes() {
    }

    /**
     * Maps a 0..1 value, clamped, to a color byte.
     *
     * @param unit the value to encode
     * @return the byte in [0, 255]
     */
    public static int toByte(float unit) {
        return Math.round(Math.min(1f, Math.max(0f, unit)) * BYTE_MAX);
    }
}
