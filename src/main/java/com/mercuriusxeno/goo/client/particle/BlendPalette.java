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

    /**
     * Decay's gnats and Infest's spray drips, from zombie goo's bright green,
     * 7AA85A, to its meat brown edge, 5E4428.
     * decay-gnats-degrade-each-block-once
     * zombie-goo-is-colored-after-zombie-flesh-and-decay-stops-being-maroon
     * zombie-goo-is-the-undead-and-takes-the-decay-abilities
     */
    public static final BlendPalette ZOMBIE_ROT = new BlendPalette(
            0x7A / 255f, 0xA8 / 255f, 0x5A / 255f, 0x5E / 255f, 0x44 / 255f, 0x28 / 255f);
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
     * Creep's spray drips, from sculk's bright cyan, 29DFEB, to its wheel
     * teal, 0F2430, the two the sculk goo type's definition names.
     * host-and-spores-combine-into-one-residual
     */
    public static final BlendPalette CREEP_TEALS = new BlendPalette(
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
     * Nectar's spray drips, from jelly's bright amber, FFB238, to its wheel
     * orange, C2410C, the two the jelly goo type's definition names.
     * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
     */
    public static final BlendPalette NECTAR_JELLY_ORANGES = new BlendPalette(
            0xFF / 255f, 0xB2 / 255f, 0x38 / 255f, 0xC2 / 255f, 0x41 / 255f, 0x0C / 255f);
    /**
     * Signal's spray drips, from pulse's bright red, F42828, to its wheel
     * red, CC0000, the two the pulse goo type's definition names.
     * extend-is-dropped-for-signal-an-in-world-pulse-goo
     */
    public static final BlendPalette SIGNAL_PULSE_REDS = new BlendPalette(
            0xF4 / 255f, 0x28 / 255f, 0x28 / 255f, 0xCC / 255f, 0x00 / 255f, 0x00 / 255f);
    /**
     * Spikes' spray drips, from metal's bright silver, E8E8E8, to its wheel
     * silver, C0C0C0, the two the metal goo type's definition names.
     * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
     */
    public static final BlendPalette SPIKES_METAL_SILVERS = new BlendPalette(
            0xE8 / 255f, 0xE8 / 255f, 0xE8 / 255f, 0xC0 / 255f, 0xC0 / 255f, 0xC0 / 255f);
    /**
     * Vines' spray drips, from leaf's bright green, A6F078, to its wheel
     * green, 7EC850, the two the leaf goo type's definition names.
     * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
     */
    public static final BlendPalette VINES_LEAF_GREENS = new BlendPalette(
            0xA6 / 255f, 0xF0 / 255f, 0x78 / 255f, 0x7E / 255f, 0xC8 / 255f, 0x50 / 255f);

    /**
     * Gloom's spray drips, from nether's bright maroon, 8C3A48, to its edge
     * obsidian, 2E1C44, the two the nether goo type's definition names.
     * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
     * nether-is-a-deeper-maroon-mixed-with-obsidians-colors
     */
    public static final BlendPalette GLOOM_NETHER_MAROONS = new BlendPalette(
            0x8C / 255f, 0x3A / 255f, 0x48 / 255f, 0x2E / 255f, 0x1C / 255f, 0x44 / 255f);

    /**
     * Stitch's spray drips, from yarn's highlight tan, CBAE88, to its wheel
     * tan, B88A5E, the two tans the yarn goo type's definition names.
     * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
     */
    public static final BlendPalette STITCH_YARN_TANS = new BlendPalette(
            0xCB / 255f, 0xAE / 255f, 0x88 / 255f, 0xB8 / 255f, 0x8A / 255f, 0x5E / 255f);

    /**
     * Nitro's spray drips, from unstable's bright green, 88FF44, to its wheel
     * green, 39FF14, the two the unstable goo type's definition names.
     * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
     */
    public static final BlendPalette NITRO_UNSTABLE_GREENS = new BlendPalette(
            0x88 / 255f, 0xFF / 255f, 0x44 / 255f, 0x39 / 255f, 0xFF / 255f, 0x14 / 255f);

    /**
     * Phase's spray drips, from quantum's highlight blue, 6AD0F0, to its
     * wheel blue, 3AA0E0, the two blues the quantum goo type's definition
     * names.
     * phase-goo-puts-what-stands-in-it-out-of-phase
     */
    public static final BlendPalette PHASE_QUANTUM_BLUES = new BlendPalette(
            0x6A / 255f, 0xD0 / 255f, 0xF0 / 255f, 0x3A / 255f, 0xA0 / 255f, 0xE0 / 255f);

    /**
     * Mortar's spray drips, from rock's bright gray, B4B6B8, to its wheel
     * brown, 7A5C44, the two the rock goo type's definition names.
     * mortar-is-rocks-residual-a-petrification-and-hardening-goo
     */
    public static final BlendPalette MORTAR_ROCK_GRAYS = new BlendPalette(
            0xB4 / 255f, 0xB6 / 255f, 0xB8 / 255f, 0x7A / 255f, 0x5C / 255f, 0x44 / 255f);

    /**
     * Dowsing's spray drips, from Astral's starlight, its bright FFF4B0, to
     * its night blue, its highlight 2A3C9A.
     * astral-world-goo-is-dowsing-an-x-ray-of-ore
     */
    public static final BlendPalette DOWSING_STARLIGHT_BLUES = new BlendPalette(
            0xFF / 255f, 0xF4 / 255f, 0xB0 / 255f, 0x2A / 255f, 0x3C / 255f, 0x9A / 255f);

    /**
     * Ooze's spray drips, from xeno's bright slate, 9282F5, to its wheel
     * slate, 6A5ACD, the two the xeno goo type's definition names.
     * ooze-is-xenos-world-goo-that-turns-slimes-into-random-hostiles
     */
    public static final BlendPalette OOZE_XENO_SLATES = new BlendPalette(
            0x92 / 255f, 0x82 / 255f, 0xF5 / 255f, 0x6A / 255f, 0x5A / 255f, 0xCD / 255f);

    /**
     * Fortune's spray drips, from ornate's edge gold, FFEC4F, to its wheel
     * gold, D8901A, the two the ornate goo type's definition names.
     * fortune-is-ornates-residual-that-makes-things-drop-more
     */
    public static final BlendPalette FORTUNE_ORNATE_GOLDS = new BlendPalette(
            0xFF / 255f, 0xEC / 255f, 0x4F / 255f, 0xD8 / 255f, 0x90 / 255f, 0x1A / 255f);

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
