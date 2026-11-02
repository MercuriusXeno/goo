package com.mercuriusxeno.goo.client.workbench;

import com.mojang.blaze3d.platform.cursor.CursorType;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Picks the manager's cursor each frame: the pointer hand while shift is held over a link a shift + left click would
 * follow, and otherwise nothing, so the frame keeps the default cursor it starts with; the game applies the frame's
 * cursor once the frame is drawn, so the hand is requested on the frame rather than selected on the window. As the
 * manager closes, the window takes the default back.
 * Decision shift-over-a-link-shows-a-pixel-pointer-hand.
 */
final class LinkCursor {
    private final Consumer<CursorType> selectOnWindow;
    private final Supplier<CursorType> hand;

    /**
     * @param selectOnWindow selects a cursor on the window, as Window.selectCursor does
     * @param hand           the pointer hand, built the first time it is asked for
     */
    LinkCursor(Consumer<CursorType> selectOnWindow, Supplier<CursorType> hand) {
        this.selectOnWindow = selectOnWindow;
        this.hand = hand;
    }

    /**
     * @param targets       the frame's click areas, which say whether shift is held
     * @param x             the cursor's x
     * @param y             the cursor's y
     * @param requestOnFrame asks the frame for a cursor, as GuiGraphicsExtractor.requestCursor does
     */
    void frame(ClickTargets targets, double x, double y, Consumer<CursorType> requestOnFrame) {
        if (targets.shiftHeld() && targets.followAt(x, y)) {
            requestOnFrame.accept(hand.get());
        }
    }

    /**
     * Gives the window back its default cursor, as the manager closes.
     */
    void release() {
        selectOnWindow.accept(CursorType.DEFAULT);
    }
}
