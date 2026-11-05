package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.components.EditBox;
import org.jspecify.annotations.Nullable;

/**
 * The one search row every list stands at its top: the box 14 tall at the list's top, stretching to the controls at
 * the row's right end one gap short of them, the list's create control among those controls, and the list one gap
 * below (decision one-search-row-per-list-with-its-create-control-beside-it).
 */
final class SearchRow {
    /** The search box's height, a button's. */
    static final int HEIGHT = ButtonPainter.HEIGHT;
    /** The room between the box and its controls, and between the row and the list below. */
    static final int GAP = RowLayout.BUTTON_GAP;

    private SearchRow() {
    }

    /**
     * Where a search row's pieces stand.
     *
     * @param boxLeft      the box's left edge, the row's
     * @param top          the box's top, the list's top
     * @param boxRight     the box's right edge
     * @param controlsLeft where the controls start, at the row's right end
     * @param listTop      where the list below starts
     */
    record Laid(int boxLeft, int top, int boxRight, int controlsLeft, int listTop) {
    }

    /**
     * @param left          the row's left edge
     * @param top           its top
     * @param right         its right edge
     * @param controlsWidth the controls' width, every gap between them included, 0 for none
     * @return the row laid
     */
    static Laid lay(int left, int top, int right, int controlsWidth) {
        int controlsLeft = right - controlsWidth;
        int boxRight = controlsWidth == 0 ? right : controlsLeft - GAP;
        return new Laid(left, top, Math.max(left + 1, boxRight), controlsLeft, top + HEIGHT + GAP);
    }

    /**
     * Stands the search box where the row lays it.
     *
     * @param box  the search box, null before the pane's init
     * @param laid the row
     */
    static void place(@Nullable EditBox box, Laid laid) {
        if (box != null) {
            box.setPosition(laid.boxLeft(), laid.top());
            box.setWidth(laid.boxRight() - laid.boxLeft());
        }
    }
}
