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
        if (runs.isEmpty()) {
            return ButtonPainter.HEIGHT;
        }
        int contentHeight = ExpressionLines.height(font, runs, contentWidth(font, width));
        return Math.max(ButtonPainter.HEIGHT, contentHeight + CONTENT_INSET + CONTENT_INSET);
    }

    /**
     * @param font the font
     * @param runs the expression's runs, empty where no explicit value stands
     * @param top  the button's top
     * @return the top of the button's first line of text, so a label beside it stands on that line
     */
    static int textTop(Font font, List<ExpressionLines.Run> runs, int top) {
        return runs.isEmpty() ? top + ButtonPainter.TEXT_NUDGE
                : top + CONTENT_INSET + ExpressionLines.textOffset(font, runs);
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
        int height = height(font, runs, place.right() - place.left());
        int frameRight = runs.isEmpty() ? place.left() + ButtonPainter.width(font, NOT_SET)
                : place.right() - ButtonPainter.width(font, PENCIL) + 1;
        ButtonPainter.Box frame = new ButtonPainter.Box(place.left(), place.top(), frameRight - place.left(), height);
        ButtonPainter.drawFrame(graphics, frame);
        ButtonPainter.reserve(targets, frame, place.open(), null);
        drawPencil(graphics, font, frame, textTop(font, runs, place.top()), targets, place.open());
        if (runs.isEmpty()) {
            graphics.text(font, NOT_SET, place.left() + ButtonPainter.PADDING, place.top() + ButtonPainter.TEXT_NUDGE,
                    Palette.MUTED);
        } else {
            ExpressionLines.draw(font, runs, place.left() + ButtonPainter.PADDING, place.top() + CONTENT_INSET,
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
        graphics.text(font, PENCIL, pencil.left() + ButtonPainter.PADDING, textTop, Palette.LINK);
        ButtonPainter.reserve(targets, pencil, open, null);
    }
}
