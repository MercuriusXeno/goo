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
    private static final int LINE_HEIGHT = ExpressionLines.TEXT_LINE_HEIGHT;
    private static final int ACTION_GAP = 12;
    private static final int HALF = 2;
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

    void extract(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        Optional<DraftConfirm.Dialog> dialog = confirm.dialog();
        if (dialog.isEmpty()) {
            return;
        }
        int textWidth = WIDTH - PADDING * HALF;
        List<ExpressionLines.Run> body = ExpressionLines.textRuns(dialog.get().text());
        int bodyHeight = ExpressionLines.height(font, body, textWidth);
        int height = PADDING * HALF + bodyHeight + LINE_HEIGHT * (HALF + 1);
        // Decision one-wheel-step-and-one-dialog-primitive.
        GroupsPane.Bounds card = Dialog.open(graphics, targets, WIDTH, height);
        int x = card.left() + PADDING;
        int y = card.top() + PADDING;
        graphics.text(font, dialog.get().title(), x, y, Palette.TEXT);
        y += LINE_HEIGHT * HALF;
        ExpressionLines.draw(Lettering.full(font), body, x, y, textWidth, new ExpressionLines.Painter(graphics, null,
                targets, Palette.MUTED, Palette.MUTED, (name, linkX, linkY, width) -> { }));
        drawChoices(graphics, font, x, y + bodyHeight + LINE_HEIGHT, targets);
    }

    private void drawChoices(GuiGraphicsExtractor graphics, Font font, int left, int y, ClickTargets targets) {
        int x = left;
        for (DraftConfirm.Choice choice : confirm.choices()) {
            Runnable pressed = () -> onChoice.accept(choice);
            ButtonPainter.Box box = losesWork(choice)
                    ? ButtonPainter.drawColored(graphics, font, choice.label(), Palette.DANGER, x,
                            y - ButtonPainter.PADDING, ButtonPainter.HEIGHT, targets, pressed, null)
                    : ButtonPainter.drawAtText(graphics, font, choice.label(), x, y, targets, pressed);
            x = box.right() + ACTION_GAP;
        }
    }

    // Promote overwrites the value file and Close without saving drops the unsaved changes, so both read red.
    private static boolean losesWork(DraftConfirm.Choice choice) {
        return choice == DraftConfirm.Choice.CONFIRM || choice == DraftConfirm.Choice.CLOSE_UNSAVED;
    }
}
