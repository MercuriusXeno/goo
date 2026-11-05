package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Paints a drag rail: a bar {@link ControlFlow#RAIL_WIDTH} wide with three grip lines centered in it, so it reads as
 * a thing to drag (decision rails-fill-the-left-edge-of-their-box).
 */
final class Rails {
    private static final int GRIP_LINES = 3;
    private static final int GRIP_PITCH = 2;
    private static final int HALF = 2;

    private Rails() {
    }

    /**
     * Paints a rail whose thing has somewhere to drop.
     *
     * @param graphics the frame's graphics
     * @param left     the rail's left edge
     * @param top      its top
     * @param bottom   its bottom
     */
    static void draw(GuiGraphicsExtractor graphics, int left, int top, int bottom) {
        draw(graphics, left, top, bottom, Palette.RAIL, Palette.GRIP);
    }

    /**
     * Paints a rail whose thing has nowhere to drop, dark gray, so no rail offers a drag that does nothing.
     *
     * @param graphics the frame's graphics
     * @param left     the rail's left edge
     * @param top      its top
     * @param bottom   its bottom
     */
    static void drawLocked(GuiGraphicsExtractor graphics, int left, int top, int bottom) {
        draw(graphics, left, top, bottom, Palette.LOCKED_RAIL, Palette.LOCKED_GRIP);
    }

    private static void draw(GuiGraphicsExtractor graphics, int left, int top, int bottom, int fill, int grips) {
        graphics.fill(left, top, left + ControlFlow.RAIL_WIDTH, bottom, fill);
        int first = (top + bottom) / HALF - GRIP_PITCH * (GRIP_LINES / HALF);
        for (int line = 0; line < GRIP_LINES; line++) {
            int gripTop = first + line * GRIP_PITCH;
            graphics.fill(left + 1, gripTop, left + ControlFlow.RAIL_WIDTH - 1, gripTop + 1, grips);
        }
    }
}
