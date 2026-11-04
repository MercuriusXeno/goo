package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

/**
 * Draws a value in read mode as one button on every tab: the read-only expression inside a button frame with a
 * pencil segment at its right, or, where no explicit value stands, a button reading "Value: Not explicitly set" with
 * the same pencil; a click on the frame or the pencil opens the builder (decisions
 * read-mode-builder-is-a-button-with-a-pencil-segment, no-value-reads-value-not-explicitly-set-as-a-pencil-button).
 */
final class ReadValueButton {
    /** What a value with no explicit expression reads. */
    static final String NOT_SET = "Value: Not explicitly set";
    /** The pencil segment's label. */
    static final String PENCIL = "✎";
    private static final int NOT_SET_COLOR = 0xFFA0A0A0;
    // The expression stands a pixel in from the frame's top border and a pixel above its bottom one.
    private static final int CONTENT_INSET = 2;

    private ReadValueButton() {
    }

    /**
     * @param font  the font
     * @param runs  the expression's runs, empty where no explicit value stands
     * @param width the room the button spans, pencil included
     * @return the height the button takes
     */
    static int height(Font font, List<ExpressionLines.Run> runs, int width) {
        return height(font, AmountGridLine.NONE, runs, width);
    }

    /**
     * @param font   the font
     * @param amount the amount line above the expression, none where the value draws none
     * @param runs   the expression's runs, empty where no explicit value stands
     * @param width  the room the button spans, pencil included
     * @return the height the button takes
     */
    static int height(Font font, AmountGridLine amount, List<ExpressionLines.Run> runs, int width) {
        if (runs.isEmpty()) {
            return ButtonPainter.HEIGHT;
        }
        int contentHeight = amount.height() + ExpressionLines.height(font, runs, contentWidth(font, width));
        return Math.max(ButtonPainter.HEIGHT, contentHeight + CONTENT_INSET + CONTENT_INSET);
    }

    /**
     * @param font the font
     * @param runs the expression's runs, empty where no explicit value stands
     * @param top  the button's top
     * @return the top of the button's first line of text, so a label beside it stands on that line
     */
    static int textTop(Font font, List<ExpressionLines.Run> runs, int top) {
        return textTop(font, AmountGridLine.NONE, runs, top);
    }

    /**
     * @param font   the font
     * @param amount the amount line above the expression, none where the value draws none
     * @param runs   the expression's runs, empty where no explicit value stands
     * @param top    the button's top
     * @return the top of the button's first line of text, the amount line's where one draws
     */
    static int textTop(Font font, AmountGridLine amount, List<ExpressionLines.Run> runs, int top) {
        if (runs.isEmpty()) {
            return top + ButtonPainter.TEXT_NUDGE;
        }
        return top + CONTENT_INSET + (amount.isEmpty() ? ExpressionLines.textOffset(font, runs)
                : amount.textOffset(font));
    }

    private static int contentWidth(Font font, int width) {
        return Math.max(1, width - ButtonPainter.width(font, PENCIL) - ButtonPainter.PADDING - ButtonPainter.PADDING);
    }

    /**
     * Where a read value draws and what a click on it does.
     *
     * @param left  the button's left edge
     * @param top   its top edge
     * @param right the right edge the pencil ends at, for an expression; an unset value's button takes its label's
     *              width
     * @param open  opens the builder over the value
     */
    record Place(int left, int top, int right, Runnable open) {
    }

    /**
     * Draws the button and takes its clicks, the expression's own links laid after so a shift + left click on one
     * follows it.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param runs     the expression's runs, empty where no explicit value stands
     * @param place    where it draws and what a click does
     * @param painter  how the expression's runs paint
     * @param targets  the frame's click areas
     * @return the height it took
     */
    static int draw(GuiGraphicsExtractor graphics, Font font, List<ExpressionLines.Run> runs, Place place,
                    ExpressionLines.Painter painter, ClickTargets targets) {
        return draw(graphics, font, new Content(AmountGridLine.NONE, runs), place, painter, targets);
    }

    /**
     * What a read value's button holds: the amount line, then the expression below it.
     *
     * @param amount the amount line, none where the value draws none
     * @param runs   the expression's runs, empty where no explicit value stands
     */
    record Content(AmountGridLine amount, List<ExpressionLines.Run> runs) {
    }

    /**
     * Draws the button with the amount line above the expression and takes its clicks.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param content  the amount line and the expression's runs
     * @param place    where it draws and what a click does
     * @param painter  how the amount line's and the expression's runs paint
     * @param targets  the frame's click areas
     * @return the height it took
     */
    static int draw(GuiGraphicsExtractor graphics, Font font, Content content, Place place,
                    ExpressionLines.Painter painter, ClickTargets targets) {
        List<ExpressionLines.Run> runs = content.runs();
        AmountGridLine amount = content.amount();
        int height = height(font, amount, runs, place.right() - place.left());
        int frameRight = runs.isEmpty() ? place.left() + ButtonPainter.width(font, NOT_SET)
                : place.right() - ButtonPainter.width(font, PENCIL) + 1;
        ButtonPainter.Box frame = new ButtonPainter.Box(place.left(), place.top(), frameRight - place.left(), height);
        ButtonPainter.drawFrame(graphics, frame);
        ButtonPainter.reserve(targets, frame, place.open(), null);
        drawPencil(graphics, font, frame, textTop(font, amount, runs, place.top()), targets, place.open());
        if (runs.isEmpty()) {
            graphics.text(font, NOT_SET, place.left() + ButtonPainter.PADDING, place.top() + ButtonPainter.TEXT_NUDGE,
                    NOT_SET_COLOR);
        } else {
            int contentLeft = place.left() + ButtonPainter.PADDING;
            int contentTop = place.top() + CONTENT_INSET;
            amount.draw(font, contentLeft, contentTop, painter);
            ExpressionLines.draw(font, runs, contentLeft, contentTop + amount.height(),
                    contentWidth(font, place.right() - place.left()), painter);
        }
        return height;
    }

    // The pencil segment shares the frame's right border and spans its height, its glyph on the value's first line.
    private static void drawPencil(GuiGraphicsExtractor graphics, Font font, ButtonPainter.Box frame, int textTop,
                                   ClickTargets targets, Runnable open) {
        ButtonPainter.Box pencil = new ButtonPainter.Box(frame.right() - 1, frame.top(),
                ButtonPainter.width(font, PENCIL), frame.height());
        ButtonPainter.drawFrame(graphics, pencil);
        graphics.text(font, PENCIL, pencil.left() + ButtonPainter.PADDING, textTop, ButtonPainter.LINK);
        ButtonPainter.reserve(targets, pencil, open, null);
    }
}
