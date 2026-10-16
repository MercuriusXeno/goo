package com.mercuriusxeno.goo.client.ability;

/**
 * The two solid colors an Unmake stream is painted with: the block's, the
 * plain mean of its particle sprite's own pixels with nothing multiplied in,
 * and the goo's, its types' tints blended by their shares of the volume.
 * decision unmake-skin-is-a-solid-sampled-color
 */
public final class DrinkColors {

    /** The color of something with no pixels or no goo to read. */
    static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int OPAQUE = 0xFF000000;
    private static final int CHANNEL = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int[] SHIFTS = {RED_SHIFT, GREEN_SHIFT, 0};

    private DrinkColors() {
    }

    /**
     * @param pixels a sprite's raw pixels, ARGB
     * @return the mean of its pixels that show at all, opaque; white where none shows
     */
    public static int blockColorOf(int[] pixels) {
        long[] sums = new long[SHIFTS.length];
        long shown = 0;
        for (int pixel : pixels) {
            if ((pixel >>> ALPHA_SHIFT) == 0) {
                continue;
            }
            shown++;
            for (int channel = 0; channel < SHIFTS.length; channel++) {
                sums[channel] += (pixel >>> SHIFTS[channel]) & CHANNEL;
            }
        }
        if (shown == 0) {
            return OPAQUE_WHITE;
        }
        int color = OPAQUE;
        for (int channel = 0; channel < SHIFTS.length; channel++) {
            color |= (int) (sums[channel] / shown) << SHIFTS[channel];
        }
        return color;
    }

    /**
     * @param tints  each goo type's tint, ARGB
     * @param shares each type's share of the volume, in the same order
     * @return the tints blended by their shares, opaque; white where nothing holds a share
     */
    public static int gooColorOf(int[] tints, float[] shares) {
        double[] sums = new double[SHIFTS.length];
        double whole = 0;
        for (int type = 0; type < tints.length; type++) {
            whole += shares[type];
            for (int channel = 0; channel < SHIFTS.length; channel++) {
                sums[channel] += shares[type] * ((tints[type] >>> SHIFTS[channel]) & CHANNEL);
            }
        }
        if (whole <= 0) {
            return OPAQUE_WHITE;
        }
        int color = OPAQUE;
        for (int channel = 0; channel < SHIFTS.length; channel++) {
            color |= (int) Math.round(sums[channel] / whole) << SHIFTS[channel];
        }
        return color;
    }

    /**
     * The skin's color at a point of a stream: the block's color at the
     * block crossfading to the goo's at the hand by the point's share of the
     * route, the crossfade each of the drink's splats wears.
     * decision unmake-skin-is-a-solid-sampled-color
     * decision diagnose-then-fix-unmake-metallic-color
     *
     * @param blockColor the block's color, ARGB
     * @param gooColor   the goo's color, ARGB
     * @param route      the point's share of the route, 0 at the block to 1 at the hand
     * @return the skin's color there, opaque
     */
    public static int skinAt(int blockColor, int gooColor, double route) {
        double toGoo = Math.clamp(route, 0, 1);
        int color = OPAQUE;
        for (int shift : SHIFTS) {
            double block = (blockColor >>> shift) & CHANNEL;
            double goo = (gooColor >>> shift) & CHANNEL;
            color |= (int) Math.round(block + (goo - block) * toGoo) << shift;
        }
        return color;
    }
}
