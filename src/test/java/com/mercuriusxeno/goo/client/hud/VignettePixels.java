package com.mercuriusxeno.goo.client.hud;

/**
 * A canvas that rasterizes every fill into an ARGB pixel grid the way the
 * GUI pipeline blends it, source alpha over what stands, rounding to eight
 * bits a channel after each fill as a framebuffer does, so two draws that
 * cover the same pixels at the same colors leave the same grid.
 */
final class VignettePixels implements VignetteCanvas {

    private static final int CHANNEL = 0xFF;
    private static final double OPAQUE = 255.0;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;

    private final int width;
    private final int height;
    private final int[] argb;
    private int fills;

    VignettePixels(int width, int height) {
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
    }

    @Override
    public int guiWidth() {
        return width;
    }

    @Override
    public int guiHeight() {
        return height;
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        fills++;
        int left = Math.max(0, Math.min(minX, maxX));
        int right = Math.min(width, Math.max(minX, maxX));
        int top = Math.max(0, Math.min(minY, maxY));
        int bottom = Math.min(height, Math.max(minY, maxY));
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                argb[y * width + x] = over(color, argb[y * width + x]);
            }
        }
    }

    private static int over(int source, int under) {
        double alpha = (source >>> ALPHA_SHIFT) / OPAQUE;
        int outAlpha = (int) Math.min(CHANNEL, Math.round((source >>> ALPHA_SHIFT) + (under >>> ALPHA_SHIFT) * (1 - alpha)));
        int red = blend(source >> RED_SHIFT & CHANNEL, under >> RED_SHIFT & CHANNEL, alpha);
        int green = blend(source >> GREEN_SHIFT & CHANNEL, under >> GREEN_SHIFT & CHANNEL, alpha);
        int blue = blend(source & CHANNEL, under & CHANNEL, alpha);
        return outAlpha << ALPHA_SHIFT | red << RED_SHIFT | green << GREEN_SHIFT | blue;
    }

    private static int blend(int source, int under, double alpha) {
        return (int) Math.min(CHANNEL, Math.round(source * alpha + under * (1 - alpha)));
    }

    /**
     * @return every pixel, row by row, ARGB
     */
    int[] argb() {
        return argb.clone();
    }

    /**
     * @return the fills drawn on the grid
     */
    int fills() {
        return fills;
    }
}
