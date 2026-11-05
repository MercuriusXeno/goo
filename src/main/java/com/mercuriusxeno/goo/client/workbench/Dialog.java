package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one confirm dialog every pane opens: the whole screen dimmed at one alpha and shielded from clicks, and a card
 * centered on it for the dialog's words and buttons (decision one-wheel-step-and-one-dialog-primitive).
 */
final class Dialog {
    /** The dim laid over the whole screen behind a dialog. */
    static final int DIM = Palette.DIALOG_DIM;
    /** The dialog's card. */
    static final int CARD = Palette.DIALOG_CARD;
    private static final int HALF = 2;

    private Dialog() {
    }

    /**
     * Dims the screen on a stratum over the frame, shields every click behind the dialog, and fills its card.
     *
     * @param graphics the frame's graphics
     * @param targets  the frame's click areas
     * @param width    the card's width
     * @param height   the card's height
     * @return the card's bounds, centered on the screen
     */
    static GroupsPane.Bounds open(GuiGraphicsExtractor graphics, ClickTargets targets, int width, int height) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        graphics.nextStratum();
        graphics.fill(0, 0, screenWidth, screenHeight, DIM);
        targets.addShield(0, 0, screenWidth, screenHeight, () -> { });
        int left = (screenWidth - width) / HALF;
        int top = (screenHeight - height) / HALF;
        GroupsPane.Bounds card = new GroupsPane.Bounds(left, top, left + width, top + height);
        graphics.fill(card.left(), card.top(), card.right(), card.bottom(), CARD);
        return card;
    }
}
