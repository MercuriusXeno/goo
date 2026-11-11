package com.mercuriusxeno.goo.client.particle;

/**
 * The two colors a gnat-stream particle blends between, each one drawn at a
 * random point from the light end to the dark.
 *
 * @param lightRed   the light end's red
 * @param lightGreen the light end's green
 * @param lightBlue  the light end's blue
 * @param darkRed    the dark end's red
 * @param darkGreen  the dark end's green
 * @param darkBlue   the dark end's blue
 */
public record GnatPalette(float lightRed, float lightGreen, float lightBlue,
                          float darkRed, float darkGreen, float darkBlue) {

    /** Decay's maroon gnats (decision decay-gnats-degrade-each-block-once). */
    public static final GnatPalette MAROON = new GnatPalette(0.75f, 0.12f, 0.2f, 0.5f, 0.06f, 0.11f);
    /**
     * Goop's slime mist, in the slime greens Goop's icon wears.
     * goop-sprays-a-slime-colored-mist
     */
    public static final GnatPalette SLIME_MIST = new GnatPalette(0.55f, 0.84f, 0.51f, 0.32f, 0.63f, 0.24f);
}
