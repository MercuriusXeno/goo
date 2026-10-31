package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Draws the Promote, Revert or close confirm over the whole manager, as the page's draft dialog stands over its page, and
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
    private final DraftConfirm confirm;
    private final Consumer<DraftConfirm.Choice> onChoice;

    DraftPane(DraftConfirm confirm, Consumer<DraftConfirm.Choice> onChoice) {
        this.confirm = confirm;
        this.onChoice = onChoice;
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
        targets.addShield(0, 0, screenWidth, screenHeight, () -> { });
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
        for (DraftConfirm.Choice choice : confirm.choices()) {
            Runnable pressed = () -> onChoice.accept(choice);
            ButtonPainter.Box box = losesWork(choice)
                    ? ButtonPainter.drawColored(graphics, font, choice.label(), RED, x, y - ButtonPainter.PADDING,
                            ButtonPainter.HEIGHT, targets, pressed, null)
                    : ButtonPainter.drawAtText(graphics, font, choice.label(), x, y, targets, pressed);
            x = box.right() + ACTION_GAP;
        }
    }

    // Promote overwrites the value file and Close without saving drops the unsaved changes, so both read red.
    private static boolean losesWork(DraftConfirm.Choice choice) {
        return choice == DraftConfirm.Choice.CONFIRM || choice == DraftConfirm.Choice.CLOSE_UNSAVED;
    }
}
