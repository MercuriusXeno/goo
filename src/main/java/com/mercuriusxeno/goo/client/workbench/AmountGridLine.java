package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A prefab's evaluated amount as its read view draws it above the expression: one cell per goo type, its icon beside
 * its amount, laid as the tooltip's grid of ceil(sqrt(count)) columns filled row by row, every column as wide as the
 * widest cell so the icons align down each column; the open paren rides the first amount and the close paren the
 * last, so no paren stands alone. A scalar's amount is one cell.
 * Decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount.
 * Decision tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits.
 */
final class AmountGridLine {
    /** The line a value draws no amount for: a plain number, a blank value or a failed evaluation. */
    static final AmountGridLine NONE = new AmountGridLine(List.of());
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String COMMA = ",";
    // The room between one column's widest cell and the next column's icon.
    private static final int COLUMN_GAP = 6;

    private final List<List<ExpressionLines.Run>> cells;
    private final int columns;

    private AmountGridLine(List<List<ExpressionLines.Run>> cells) {
        this.cells = cells;
        this.columns = Math.max(1, (int) Math.ceil(Math.sqrt(cells.size())));
    }

    /**
     * @param amounts each goo type with its amount, in the order the line lays them
     * @return the line over a typed value's amounts
     */
    static AmountGridLine typed(Map<String, String> amounts) {
        List<String> types = List.copyOf(amounts.keySet());
        List<List<ExpressionLines.Run>> cells = new ArrayList<>();
        for (int index = 0; index < types.size(); index++) {
            String type = types.get(index);
            cells.add(List.of(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, type, type),
                    amount(punctuated(amounts.get(type), index, types.size()))));
        }
        return new AmountGridLine(cells);
    }

    /**
     * @param amount a scalar value's amount
     * @return the line over it, one cell
     */
    static AmountGridLine scalar(String amount) {
        return new AmountGridLine(List.of(List.of(amount(OPEN + amount + CLOSE))));
    }

    private static ExpressionLines.Run amount(String text) {
        return new ExpressionLines.Run(ExpressionLines.Kind.NUMBER, text, null);
    }

    // The first amount opens the paren, the last closes it, and a comma follows each but the last.
    private static String punctuated(String amount, int index, int count) {
        String opened = index == 0 ? OPEN + amount : amount;
        return index == count - 1 ? opened + CLOSE : opened + COMMA;
    }

    /**
     * @return whether the line draws nothing
     */
    boolean isEmpty() {
        return cells.isEmpty();
    }

    private int rows() {
        return (cells.size() + columns - 1) / columns;
    }

    private int rowHeight() {
        return cells.stream().mapToInt(ExpressionLines::lineHeight).max().orElse(0);
    }

    private int columnWidth(Font font) {
        return cells.stream().mapToInt(cell -> ExpressionLines.width(font, cell)).max().orElse(0) + COLUMN_GAP;
    }

    /**
     * @return the height the line takes, every row as tall as its tallest cell
     */
    int height() {
        return rows() * rowHeight();
    }

    /**
     * @param font the font
     * @return how far below the line's top its text stands, so a label beside it stands on its first row
     */
    int textOffset(Font font) {
        return cells.isEmpty() ? 0 : ExpressionLines.textOffset(font, cells.getFirst());
    }

    /**
     * @param font    the font
     * @param left    the line's left edge
     * @param top     its top
     * @param painter how a cell's icon and amount paint
     */
    void draw(Font font, int left, int top, ExpressionLines.Painter painter) {
        int columnWidth = columnWidth(font);
        int rowHeight = rowHeight();
        for (int index = 0; index < cells.size(); index++) {
            List<ExpressionLines.Run> cell = cells.get(index);
            ExpressionLines.draw(font, cell, left + index % columns * columnWidth, top + index / columns * rowHeight,
                    ExpressionLines.width(font, cell) + 1, painter);
        }
    }
}
