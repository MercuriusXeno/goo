package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.Optional;

/**
 * Draws the Promote or Revert confirm over the whole manager, as the page's draft dialog stands over its page, and
 * takes every click while it stands (decision save-applies-the-draft-live-promote-renames-it). The state lives in
 * {@link DraftConfirm}.
 */
final class DraftPane {
    private static final int WIDTH = 320;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int ACTION_GAP = 12;
    private static final int HALF = 2;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFC0C0C0;
    private static final int RED = 0xFFFF5555;
    private static final int DIM_COLOR = 0xB0000000;
    private static final int CARD_COLOR = 0xF0181818;
    private static final String CONFIRM = "Confirm";
    private static final String CANCEL = "Cancel";

    private final DraftConfirm confirm;
    private final Runnable onConfirm;

    DraftPane(DraftConfirm confirm, Runnable onConfirm) {
        this.confirm = confirm;
        this.onConfirm = onConfirm;
    }

    /**
     * @return whether a confirm stands open
     */
    boolean open() {
        return confirm.dialog().isPresent();
    }

    void extract(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight, ClickTargets targets) {
        Optional<DraftConfirm.Dialog> dialog = confirm.dialog();
        if (dialog.isEmpty()) {
            return;
        }
        graphics.nextStratum();
        graphics.fill(0, 0, screenWidth, screenHeight, DIM_COLOR);
        targets.add(0, 0, screenWidth, screenHeight, () -> { });
        int textWidth = WIDTH - PADDING * HALF;
        List<ExpressionLines.Run> body = ExpressionLines.textRuns(dialog.get().text());
        int bodyHeight = ExpressionLines.height(font, body, textWidth);
        int height = PADDING * HALF + bodyHeight + LINE_HEIGHT * (HALF + 1);
        int left = (screenWidth - WIDTH) / HALF;
        int top = (screenHeight - height) / HALF;
        graphics.fill(left, top, left + WIDTH, top + height, CARD_COLOR);
        int x = left + PADDING;
        int y = top + PADDING;
        graphics.text(font, dialog.get().title(), x, y, WHITE);
        y += LINE_HEIGHT * HALF;
        ExpressionLines.draw(font, body, x, y, textWidth, new ExpressionLines.Painter(graphics, null, targets, GRAY, GRAY,
                (name, linkX, linkY, width) -> { }));
        y += bodyHeight + LINE_HEIGHT;
        ButtonPainter.Box confirmBox = ButtonPainter.drawColored(graphics, font, CONFIRM, RED, x,
                y - ButtonPainter.PADDING, ButtonPainter.HEIGHT, targets, onConfirm, null);
        ButtonPainter.drawAtText(graphics, font, CANCEL, confirmBox.right() + ACTION_GAP, y, targets, confirm::cancel);
    }
}
