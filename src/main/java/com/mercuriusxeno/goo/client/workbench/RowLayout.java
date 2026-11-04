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
    // Decision spacing-rules-are-one-set-across-every-pane: every pane draws its gaps from these three.
    /** The room between a rule and the buttons above or below it. */
    static final int RULE_GAP = 2;
    /** The room between neighbouring buttons, as the rename pencil stands past its name. */
    static final int BUTTON_GAP = 3;
    /** The room the builder's gray box keeps outside its contents. */
    static final int BOX_MARGIN = 3;
    /** The room a row leaves under its last buttons: the rule gap and the rule's own pixel. */
    static final int RULE_SPAN = RULE_GAP + 1;
    /**
     * The room a sprite row keeps between its sprite and the rule below it, so no rule touches a sprite (decision
     * rule-takes-padding-above-it).
     */
    static final int RULE_PADDING = RULE_GAP;
    /** A row holding a sprite, from its top to the next one's: the row and the rule's padding. */
    static final int SPRITE_ROW_PITCH = SPRITE_ROW_HEIGHT + RULE_PADDING;
    /** The separator's dim color. */
    static final int SEPARATOR_COLOR = 0x40FFFFFF;

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
