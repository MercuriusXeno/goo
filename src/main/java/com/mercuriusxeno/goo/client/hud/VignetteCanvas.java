package com.mercuriusxeno.goo.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The surface a vignette draws on: the GUI's size and its filled rectangles,
 * so a test counts or rasterizes one frame's fills headless.
 * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
 */
interface VignetteCanvas {

    /**
     * @return the GUI's width, in GUI pixels
     */
    int guiWidth();

    /**
     * @return the GUI's height, in GUI pixels
     */
    int guiHeight();

    /**
     * Fills a rectangle.
     *
     * @param minX  its left edge
     * @param minY  its top edge
     * @param maxX  its right edge, exclusive
     * @param maxY  its bottom edge, exclusive
     * @param color its color, ARGB
     */
    void fill(int minX, int minY, int maxX, int maxY, int color);

    /**
     * @param graphics the GUI graphics a frame draws on
     * @return a canvas filling through it
     */
    static VignetteCanvas of(GuiGraphicsExtractor graphics) {
        return new VignetteCanvas() {
            @Override
            public int guiWidth() {
                return graphics.guiWidth();
            }

            @Override
            public int guiHeight() {
                return graphics.guiHeight();
            }

            @Override
            public void fill(int minX, int minY, int maxX, int maxY, int color) {
                graphics.fill(minX, minY, maxX, maxY, color);
            }
        };
    }
}
