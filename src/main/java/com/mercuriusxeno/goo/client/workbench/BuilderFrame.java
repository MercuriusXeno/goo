package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The one frame a row's value lays in on Items, Prefabs and Groups, read-only and editing alike: the row's label
 * where it has one, its evaluated amount line through the amount grid, the box one gap below that line at the
 * value's indent spanning to the row's right edge short of the delete x, the builder's panel overhang reserved, and
 * one red error line under the value when it fails to evaluate (decisions one-builder-frame-on-every-pane,
 * one-red-error-line-under-every-value).
 */
final class BuilderFrame {
    /** The room between the amount line and the box below it. */
    static final int AMOUNT_GAP = 2;
    /** The room between a row's label and its value. */
    static final int LABEL_GAP = 4;
    private static final String DELETE = "✗";
    private static final String WORD_BREAK = " ";

    private BuilderFrame() {
    }

    /**
     * A row's value as the frame lays it.
     *
     * @param row        the row's left and right edges and the value's top
     * @param label      the label standing before the value, null where the row has none
     * @param amount     the value's evaluated amount line, {@link GooAmountGrid#NONE} where it shows none
     * @param deletable  whether the row's delete x stands at its right edge, which the box stops short of
     */
    record Row(GroupsPane.Bounds row, @Nullable String label, GooAmountGrid amount, boolean deletable) {
    }

    /**
     * Where a row's value pieces stand.
     *
     * @param labelX    the label's left edge, the row's indent
     * @param valueX    the amount line's and the box's left edge
     * @param amountTop the amount line's top
     * @param boxTop    the box's top: one gap below the amount line, or the value's top where none draws
     * @param boxRight  the box's right edge
     */
    record Laid(int labelX, int valueX, int amountTop, int boxTop, int boxRight) {
    }

    /**
     * Draws the value's box: its read-only button or its open builder.
     */
    @FunctionalInterface
    interface Box {
        /**
         * @param area the box's left, top and right edges; the bottom is the view's
         * @return the height the box took
         */
        int draw(GroupsPane.Bounds area);
    }

    /**
     * What a row's value draws.
     *
     * @param editing whether the builder stands open over it, which reserves the panel's overhang above it
     * @param runs    the value's runs as the read button draws them, which a label beside it lines up with
     * @param error   the value's evaluation error, empty where it evaluates
     * @param box     draws the box
     */
    record Value(boolean editing, List<ExpressionLines.Run> runs, String error, Box box) {
    }

    /**
     * @param font the font
     * @param row  the row
     * @return where its value's pieces stand
     */
    static Laid lay(Font font, Row row) {
        int labelX = row.row().left() + ControlFlow.INDENT;
        int valueX = row.label() == null ? labelX : labelX + font.width(row.label()) + LABEL_GAP;
        int top = row.row().top();
        int boxRight = row.row().right() - (row.deletable() ? deleteInset(font) : 0);
        GooAmountGrid amount = amountWithin(font, row.amount(), valueX, boxRight);
        int boxTop = amount.isEmpty() ? top : top + amount.getHeight(font) + AMOUNT_GAP;
        return new Laid(labelX, valueX, top, boxTop, boxRight);
    }

    // Decision row-amount-grid-fills-the-width-and-wraps-past-it: the amount line takes the row's width from where it
    // draws, past the button padding, to the box's right edge.
    private static GooAmountGrid amountWithin(Font font, GooAmountGrid amount, int valueX, int boxRight) {
        return amount.within(font, boxRight - amountX(valueX));
    }

    private static int amountX(int valueX) {
        return valueX + ButtonPainter.PADDING;
    }

    /**
     * @param font the font
     * @return the room a row's delete x and the gap before it take at the row's right edge
     */
    static int deleteInset(Font font) {
        return ButtonPainter.width(font, DELETE) + RowLayout.BUTTON_GAP;
    }

    /**
     * Draws a row's value: its amount line, its box, its label and its error line.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param row      the row
     * @param value    what the value draws
     * @return the height it took, from the value's top
     */
    static int draw(GuiGraphicsExtractor graphics, Font font, Row row, Value value) {
        Laid laid = lay(font, row);
        amountWithin(font, row.amount(), laid.valueX(), laid.boxRight()).draw(graphics, font, amountX(laid.valueX()),
                laid.amountTop());
        int overhang = value.editing() ? ExpressionBuilderPane.PANEL_ABOVE : 0;
        int boxTop = laid.boxTop() + overhang;
        int height = overhang + value.box().draw(new GroupsPane.Bounds(laid.valueX(), boxTop, laid.boxRight(),
                row.row().bottom()));
        if (row.label() != null) {
            graphics.text(font, row.label(), laid.labelX(), labelTop(font, row, value, laid), Palette.MUTED);
        }
        for (String line : errorLines(font, value.error(), laid.boxRight() - laid.valueX())) {
            graphics.text(font, line, laid.valueX(), laid.boxTop() + height, Palette.DANGER);
            height += ExpressionLines.TEXT_LINE_HEIGHT;
        }
        return laid.boxTop() - laid.amountTop() + height;
    }

    /**
     * @param font      the font
     * @param row       the row
     * @param boxHeight the read button's height
     * @param error     the value's evaluation error, empty where it evaluates
     * @return the height a read-only value takes, as {@link #draw} would draw it
     */
    static int measure(Font font, Row row, int boxHeight, String error) {
        Laid laid = lay(font, row);
        int errors = errorLines(font, error, laid.boxRight() - laid.valueX()).size();
        return laid.boxTop() - laid.amountTop() + boxHeight + errors * ExpressionLines.TEXT_LINE_HEIGHT;
    }

    // The label stands on the amount line where one draws, else beside the box's first line.
    private static int labelTop(Font font, Row row, Value value, Laid laid) {
        if (!row.amount().isEmpty()) {
            return laid.amountTop() + row.amount().textOffset(font);
        }
        return value.editing() ? ControlFlow.besideBuilderTop(font, laid.boxTop() + ExpressionBuilderPane.PANEL_ABOVE)
                : ReadValueButton.textTop(font, value.runs(), laid.boxTop());
    }

    /**
     * @param font  the font
     * @param error an evaluation error
     * @param width the room it may take
     * @return its lines, wrapped at word breaks to the room given, so the whole error reads inside the row; a word
     *     wider than the room stands on a line of its own, cut to it
     */
    static List<String> errorLines(Font font, String error, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : error.split(WORD_BREAK)) {
            String joined = line.isEmpty() ? word : line + WORD_BREAK + word;
            if (!line.isEmpty() && font.width(joined) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(joined);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.stream().map(wrapped -> font.plainSubstrByWidth(wrapped, width)).toList();
    }
}
