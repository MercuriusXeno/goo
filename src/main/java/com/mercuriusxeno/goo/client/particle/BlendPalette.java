package com.mercuriusxeno.goo.client.particle;

import net.minecraft.util.ARGB;

/**
 * The two colors a particle blends between, each one drawn at a random
 * point from the light end to the dark.
 *
 * @param lightRed   the light end's red
 * @param lightGreen the light end's green
 * @param lightBlue  the light end's blue
 * @param darkRed    the dark end's red
 * @param darkGreen  the dark end's green
 * @param darkBlue   the dark end's blue
 */
public record BlendPalette(float lightRed, float lightGreen, float lightBlue,
                           float darkRed, float darkGreen, float darkBlue) {

    /** Decay's maroon gnats (decision decay-gnats-degrade-each-block-once). */
    public static final BlendPalette MAROON = new BlendPalette(0.75f, 0.12f, 0.2f, 0.5f, 0.06f, 0.11f);
    /**
     * Goop's spray drips, in the slime greens Goop's icon wears.
     * goop-and-slick-spray-throws-goo-drips-that-splat
     * goop-sprays-a-slime-colored-mist
     */
    public static final BlendPalette GOOP_SLIME_GREENS = new BlendPalette(0.55f, 0.84f, 0.51f, 0.32f, 0.63f, 0.24f);
    /**
     * Slick's spray drips, from its block texture's light slime blue, 6A9AE0, to
     * its dark slime blue, 2C7DC1. The blend passes the texture's body blue,
     * 428BC6, and leaves out its near-white highlight, C1D7FF.
     * goop-and-slick-spray-throws-goo-drips-that-splat
     * slick-spray-takes-its-blue-slime-texture-colors
     */
    public static final BlendPalette SLICK_SLIME_BLUES = new BlendPalette(
            0x6A / 255f, 0x9A / 255f, 0xE0 / 255f, 0x2C / 255f, 0x7D / 255f, 0xC1 / 255f);
    /**
     * Molten's spray drips, from blaze's bright orange, FF8E28, to its wheel
     * orange, FF6600, the two the blaze goo type's definition names.
     * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
     */
    public static final BlendPalette MOLTEN_BLAZE_ORANGES = new BlendPalette(
            0xFF / 255f, 0x8E / 255f, 0x28 / 255f, 0xFF / 255f, 0x66 / 255f, 0x00 / 255f);
    /**
     * Shatter's spray drips, from crystal's bright blue, 77E9FF, to its wheel
     * blue, 4FC1E9, the two the crystal goo type's definition names.
     * shatter-is-splattered-goo-and-shards-form-over-the-splatters
     */
    public static final BlendPalette SHATTER_CRYSTAL_BLUES = new BlendPalette(
            0x77 / 255f, 0xE9 / 255f, 0xFF / 255f, 0x4F / 255f, 0xC1 / 255f, 0xE9 / 255f);
    /**
     * Host's spray drips, from deep's bright sculk cyan, 29DFEB, to its wheel
     * teal, 0F2430, the two the deep goo type's definition names.
     * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
     */
    public static final BlendPalette HOST_DEEP_TEALS = new BlendPalette(
            0x29 / 255f, 0xDF / 255f, 0xEB / 255f, 0x0F / 255f, 0x24 / 255f, 0x30 / 255f);
    /**
     * Rime's spray drips, from frost's bright ice white, D5FFFF, to its wheel
     * blue, ADD8E6, the two the frost goo type's definition names.
     * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
     */
    public static final BlendPalette RIME_FROST_BLUES = new BlendPalette(
            0xD5 / 255f, 0xFF / 255f, 0xFF / 255f, 0xAD / 255f, 0xD8 / 255f, 0xE6 / 255f);
    /**
     * Wisps' spray drips, from glow's bright yellow, FFFF28, to its wheel
     * gold, FFD700, the two the glow goo type's definition names.
     * glow-world-goo-is-named-wisps-and-takes-the-holds-place
     */
    public static final BlendPalette WISPS_GLOW_GOLDS = new BlendPalette(
            0xFF / 255f, 0xFF / 255f, 0x28 / 255f, 0xFF / 255f, 0xD7 / 255f, 0x00 / 255f);
    /**
     * Leech's spray drips, from hex's bright violet, 8373C8, to its wheel
     * violet, 5B4BA0, the two the hex goo type's definition names.
     * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
     */
    public static final BlendPalette LEECH_HEX_VIOLETS = new BlendPalette(
            0x83 / 255f, 0x73 / 255f, 0xC8 / 255f, 0x5B / 255f, 0x4B / 255f, 0xA0 / 255f);
    /**
     * Updraft's spray drips, from typhoon's bright white, FDFFFF, to its
     * wheel green, D5F5E3, the two the typhoon goo type's definition names.
     * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
     */
    public static final BlendPalette UPDRAFT_TYPHOON_GREENS = new BlendPalette(
            0xFD / 255f, 0xFF / 255f, 0xFF / 255f, 0xD5 / 255f, 0xF5 / 255f, 0xE3 / 255f);
    /**
     * Signal's spray drips, from pulse's bright red, F42828, to its wheel
     * red, CC0000, the two the pulse goo type's definition names.
     * extend-is-dropped-for-signal-an-in-world-pulse-goo
     */
    public static final BlendPalette SIGNAL_PULSE_REDS = new BlendPalette(
            0xF4 / 255f, 0x28 / 255f, 0x28 / 255f, 0xCC / 255f, 0x00 / 255f, 0x00 / 255f);

    /**
     * The color a share of the way from the light end to the dark.
     *
     * @param blend the share, 0 at the light end and 1 at the dark
     * @return the opaque color
     */
    public int colorAt(float blend) {
        return ARGB.colorFromFloat(1f, lightRed + (darkRed - lightRed) * blend,
                lightGreen + (darkGreen - lightGreen) * blend, lightBlue + (darkBlue - lightBlue) * blend);
    }
}
