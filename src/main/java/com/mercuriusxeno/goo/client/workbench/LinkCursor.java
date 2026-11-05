package com.mercuriusxeno.goo.client.workbench;

import com.mojang.blaze3d.platform.cursor.CursorType;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Picks the manager's cursor each frame: the green plus while a held rail drag would drop a copy, the pointer hand
 * while shift is held over a link a shift + left click would follow, and otherwise nothing, so the frame keeps the
 * default cursor it starts with; the game applies the frame's cursor once the frame is drawn, so each is requested on
 * the frame rather than selected on the window. As the manager closes, the window takes the default back.
 * Decision shift-over-a-link-shows-a-pixel-pointer-hand.
 * Decision ctrl-during-a-rail-drag-shows-a-green-plus-and-drops-a-copy.
 */
final class LinkCursor {
    private final Consumer<CursorType> selectOnWindow;
    private final Supplier<CursorType> hand;
    private final Supplier<CursorType> plus;

    /**
     * @param selectOnWindow selects a cursor on the window, as Window.selectCursor does
     * @param hand           the pointer hand, built the first time it is asked for
     * @param plus           the green plus, built the first time it is asked for
     */
    LinkCursor(Consumer<CursorType> selectOnWindow, Supplier<CursorType> hand, Supplier<CursorType> plus) {
        this.selectOnWindow = selectOnWindow;
        this.hand = hand;
        this.plus = plus;
    }

    /**
     * @param targets        the frame's click areas, which say whether shift is held and whether a held drag copies
     * @param x              the cursor's x
     * @param y              the cursor's y
     * @param requestOnFrame asks the frame for a cursor, as GuiGraphicsExtractor.requestCursor does
     */
    void frame(ClickTargets targets, double x, double y, Consumer<CursorType> requestOnFrame) {
        if (targets.copying()) {
            requestOnFrame.accept(plus.get());
        } else if (targets.shiftHeld() && targets.followAt(x, y)) {
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
