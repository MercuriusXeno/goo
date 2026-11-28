package com.mercuriusxeno.goo.client.zone;

import java.util.Optional;

/**
 * One blue planet phase sprite's geometry, read as the overworld map's colors scaled by the light: where its unlit
 * columns, its terminator fade and its lit edges stand, and the factor the rim darkens each texel by.
 * decision overworld-rim-darkens-like-the-moons
 *
 * @param name       the phase's sprite name
 * @param unlitWidth the columns its unlit part spans
 * @param darkOnLeft whether the unlit part stands on the left
 */
record PlanetPhaseTexels(String name, int unlitWidth, boolean darkOnLeft) {

    static final int TEXELS = 32;
    static final int ICE = 0xDCDCDC;
    static final int MOUNTAIN = 0x76706A;
    /** The overworld map's undarkened colors: ice, mountain, forest, grass, sand, shallow sea and deep sea. */
    static final int[] MAP_COLORS = {ICE, MOUNTAIN, 0x006B00, 0x6E9930, 0xD5C98C, 0x3737DC, 0x2828B4};
    /** The texels in from a lit edge the rim darkens, as vanilla's moon darkens a quarter of its disc. */
    static final int RIM_TEXELS = 8;
    /** The terminator fade's factors, from the dark side in. */
    static final double[] FADE = {0.3, 0.45, 0.6, 0.8};
    /** The rim's factor on a lit edge's outermost line, about half as vanilla's moon's rim. */
    private static final double EDGE_FACTOR = 0.5;
    /** How far a channel may stand from its map color times the factor: the rounding to whole channel values. */
    private static final double ROUNDING = 1.0;
    /** How far past 1 a factor read back from rounded channels may stand. */
    private static final double OVER_ONE = 0.02;
    private static final int CHANNEL = 0xFF;

    /**
     * A lit texel read back as the map color it scales and the factor it is scaled by.
     *
     * @param color  the undarkened map color
     * @param factor the factor on all three channels
     */
    record MapTexel(int color, double factor) {
    }

    boolean litOnLeft() {
        return !darkOnLeft || unlitWidth == 0;
    }

    boolean litOnRight() {
        return darkOnLeft || unlitWidth == 0;
    }

    private int fromDarkSide(int x) {
        return darkOnLeft ? x : TEXELS - 1 - x;
    }

    boolean unlit(int x) {
        return fromDarkSide(x) < unlitWidth;
    }

    boolean inFade(int x) {
        int intoFade = fromDarkSide(x) - unlitWidth;
        return unlitWidth > 0 && intoFade >= 0 && intoFade < FADE.length;
    }

    double fadeFactor(int x) {
        return FADE[fromDarkSide(x) - unlitWidth];
    }

    /**
     * The rim's factor at a texel: 1 at eight texels in from every lit edge, falling evenly to about half on a lit
     * edge's outermost line, the smallest over the lit edges where they meet.
     *
     * @param x the texel's column
     * @param y the texel's row
     * @return the factor
     */
    double rimFactor(int x, int y) {
        int nearest = Math.min(y, TEXELS - 1 - y);
        if (litOnLeft()) {
            nearest = Math.min(nearest, x);
        }
        if (litOnRight()) {
            nearest = Math.min(nearest, TEXELS - 1 - x);
        }
        return nearest >= RIM_TEXELS ? 1.0 : EDGE_FACTOR + (1.0 - EDGE_FACTOR) * nearest / RIM_TEXELS;
    }

    /**
     * Reads a lit texel back as a map color and its factor: of the map colors whose channels all scale to the
     * texel's by one factor no greater than 1, the one scaled least, since ice dimmed reads as grey mountain too.
     *
     * @param rgb the texel's color
     * @return the map texel, empty where no map color scales to it
     */
    static Optional<MapTexel> readBack(int rgb) {
        MapTexel best = null;
        for (int color : MAP_COLORS) {
            double factor = (double) sum(rgb) / sum(color);
            if (factor <= 1.0 + OVER_ONE && scalesTo(color, factor, rgb) && (best == null || factor > best.factor())) {
                best = new MapTexel(color, factor);
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean scalesTo(int color, double factor, int rgb) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs((color >> shift & CHANNEL) * factor - (rgb >> shift & CHANNEL)) > ROUNDING) {
                return false;
            }
        }
        return true;
    }

    private static int sum(int rgb) {
        return (rgb >> 16 & CHANNEL) + (rgb >> 8 & CHANNEL) + (rgb & CHANNEL);
    }
}
