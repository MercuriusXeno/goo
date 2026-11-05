package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The glow a followed row takes once its pane centers it: a pale gold fill over the row's bounds that fades over
 * about a second of frames, then stops (decision followed-target-glows-briefly). It starts at the palette's glow
 * (decision one-palette-names-every-color-by-role).
 */
final class RevealGlow {
    /** How many frames the glow lasts, about a second. */
    static final int FRAMES = 60;
    private static final int ALPHA_SHIFT = 24;
    private static final int FULL_ALPHA = Palette.GLOW >>> ALPHA_SHIFT;
    private static final int RGB = Palette.GLOW & ~(FULL_ALPHA << ALPHA_SHIFT);

    private int age = FRAMES;
    private int top;
    private int height;

    /**
     * Starts the glow over a row, from the next frame drawn.
     *
     * @param rowTop    the row's top in its list's content, its scroll offset not applied
     * @param rowHeight the row's height
     */
    void start(int rowTop, int rowHeight) {
        age = 0;
        top = rowTop;
        height = rowHeight;
    }

    /**
     * @param frame how many frames the glow has drawn
     * @return its color that frame: full at its first, fading each frame after, clear from {@link #FRAMES} on
     */
    static int colorAt(int frame) {
        return alphaAt(frame) << ALPHA_SHIFT | RGB;
    }

    /**
     * @param frame how many frames the glow has drawn
     * @return its alpha that frame, strictly falling to zero at {@link #FRAMES}
     */
    static int alphaAt(int frame) {
        return frame >= FRAMES ? 0 : FULL_ALPHA * (FRAMES - frame) / FRAMES;
    }

    /**
     * Fills the glowing row, if a glow runs, and ages it one frame.
     *
     * @param graphics the frame's graphics
     * @param left     the row's left
     * @param origin   where the list's content starts on screen, its scroll offset applied
     * @param right    the row's right
     */
    void draw(GuiGraphicsExtractor graphics, int left, int origin, int right) {
        if (age >= FRAMES) {
            return;
        }
        graphics.fill(left, origin + top, right, origin + top + height, colorAt(age));
        age++;
    }
}
