package com.mercuriusxeno.goo.client.ber;

/**
 * How far a melting item has dissolved and the color its edge glows, packed into the
 * overlay coordinates the dissolve shader reads (decision dissolve-shader-on-item):
 * the low half holds the fraction in {@link #FRACTION_UNITS}, the high half the
 * glow color as RGB565.
 *
 * @param fraction the share of the item dissolved, from 0 whole to 1 gone
 * @param glowRgb  the glow color as 0xRRGGBB; any alpha byte is ignored
 */
public record DissolveGlow(float fraction, int glowRgb) {

    /** Overlay units per whole fraction; must match FRACTION_UNITS in crucible_dissolve.vsh. */
    public static final int FRACTION_UNITS = 4096;

    private static final int BYTE = 0xFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int FIVE_BIT_DROP = 3;
    private static final int SIX_BIT_DROP = 2;
    private static final int RED_565_SHIFT = 11;
    private static final int GREEN_565_SHIFT = 5;
    private static final int HIGH_HALF_SHIFT = 16;

    /**
     * Returns the fraction in overlay units, clamped to a whole item.
     *
     * @return the fraction units, from 0 to {@link #FRACTION_UNITS}
     */
    public int fractionUnits() {
        return Math.round(Math.clamp(fraction, 0f, 1f) * FRACTION_UNITS);
    }

    /**
     * Returns the glow color packed as RGB565.
     *
     * @return the 16-bit color
     */
    public int glowRgb565() {
        int red = (glowRgb >> RED_SHIFT) & BYTE;
        int green = (glowRgb >> GREEN_SHIFT) & BYTE;
        int blue = glowRgb & BYTE;
        return (red >> FIVE_BIT_DROP) << RED_565_SHIFT
                | (green >> SIX_BIT_DROP) << GREEN_565_SHIFT
                | blue >> FIVE_BIT_DROP;
    }

    /**
     * Returns the packed overlay coordinates: fraction units low, RGB565 high.
     *
     * @return the overlay coordinates
     */
    public int overlayCoords() {
        return fractionUnits() | glowRgb565() << HIGH_HALF_SHIFT;
    }
}
