package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.OptionalInt;

/**
 * The row arithmetic every listing pane draws from: an item sprite stays 16px in an 18px row (decision
 * sprites-stay-16px-and-rows-tighten-to-18px), and a dim one-pixel separator stands between consecutive entries,
 * none after the last (decision entries-draw-line-separators).
 */
final class RowLayout {
    /** An item sprite's side. */
    static final int SPRITE_SIZE = 16;
    /** A row holding a sprite. */
    static final int SPRITE_ROW_HEIGHT = 18;
    private static final int SEPARATOR_COLOR = 0x40FFFFFF;

    private RowLayout() {
    }

    /**
     * @param listTop   the list's top
     * @param index     the row's place in the list, from 0
     * @param rowHeight the pane's row height
     * @return the row's top
     */
    static int rowTop(int listTop, int index, int rowHeight) {
        return listTop + index * rowHeight;
    }

    /**
     * @param index  the row's place among the rows drawn, from 0
     * @param rowTop the row's top
     * @return the separator's y, the pixel above the row, none above the first
     */
    static OptionalInt separatorAbove(int index, int rowTop) {
        return index > 0 ? OptionalInt.of(rowTop - 1) : OptionalInt.empty();
    }

    /**
     * Draws the separator above a row where one stands.
     *
     * @param graphics the frame's graphics
     * @param index    the row's place among the rows drawn, from 0
     * @param rowTop   the row's top
     * @param left     the separator's left edge
     * @param right    its right edge
     */
    static void drawSeparatorAbove(GuiGraphicsExtractor graphics, int index, int rowTop, int left, int right) {
        separatorAbove(index, rowTop).ifPresent(y -> graphics.fill(left, y, right, y + 1, SEPARATOR_COLOR));
    }
}
