package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The one grid every list of goo amounts draws as, in a tooltip and in a row: ceil(sqrt(count)) columns filled row by
 * row, every column as wide as the widest cell so the icons align down each column, each cell a type's icon then its
 * amount in the amount role's small digits, one gap between columns, no colon and no indent; a reduced value's
 * parens stand flush round the grid in the symbols' color, one color both sides (decisions
 * one-amount-grid-draws-every-amount-list, tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
 */
public final class GooAmountGrid implements ClientTooltipComponent {
    /** The open paren a reduced value's grid stands after. */
    static final ExpressionLines.Run OPEN = new ExpressionLines.Run(ExpressionLines.Kind.FLUSH_SYMBOL, "(", null);
    /** The close paren a reduced value's grid stands before, flush against its last cell. */
    static final ExpressionLines.Run CLOSE = new ExpressionLines.Run(ExpressionLines.Kind.FLUSH_SYMBOL, ")", null);
    /** The grid a value draws no amount for: a plain number, a blank value or a failed evaluation. */
    static final GooAmountGrid NONE = new GooAmountGrid(new TooltipLine.GooAmounts(List.of()));
    // The room between one column's widest cell and the next column's icons.
    private static final int COLUMN_GAP = 4;
    private static final int ICON_GAP = 2;
    private static final int LINE_GAP = 2;
    private static final String NO_TYPE = "";

    private final List<TooltipLine.GooAmount> amounts;
    private final boolean reduced;
    private final ToIntFunction<String> sizeOf;
    private final int columns;

    /**
     * @param amounts the run of amounts the grid lays
     */
    public GooAmountGrid(TooltipLine.GooAmounts amounts) {
        this(amounts, GooTypeIcons::sizeOf);
    }

    /**
     * @param amounts the run of amounts the grid lays
     * @param sizeOf  a type icon's side by its type
     */
    GooAmountGrid(TooltipLine.GooAmounts amounts, ToIntFunction<String> sizeOf) {
        this.amounts = amounts.amounts();
        this.reduced = amounts.reduced();
        this.sizeOf = sizeOf;
        this.columns = Math.max(1, (int) Math.ceil(Math.sqrt(this.amounts.size())));
    }

    /**
     * @param amounts each goo type with its amount, in the order the grid lays them
     * @return the grid over a typed value's reduced amounts, in its parens
     */
    static GooAmountGrid reduced(Map<String, String> amounts) {
        return new GooAmountGrid(reducedLine(amounts));
    }

    /**
     * @param amounts each goo type with its amount, in the order the grid lays them
     * @return the tooltip line a typed value's reduced amounts read as
     */
    static TooltipLine.GooAmounts reducedLine(Map<String, String> amounts) {
        List<TooltipLine.GooAmount> cells = new ArrayList<>();
        amounts.forEach((type, amount) -> cells.add(new TooltipLine.GooAmount(type, amount)));
        return new TooltipLine.GooAmounts(cells, true);
    }

    /**
     * @param amount a scalar value's reduced amount
     * @return the tooltip line it reads as: one cell with no icon, in its parens
     */
    static TooltipLine.GooAmounts reducedScalar(String amount) {
        return new TooltipLine.GooAmounts(List.of(new TooltipLine.GooAmount(NO_TYPE, amount)), true);
    }

    /**
     * @return whether the grid draws nothing
     */
    boolean isEmpty() {
        return amounts.isEmpty();
    }

    /**
     * @return each cell's runs, its icon then its amount, and the parens as runs of their own round them, for a
     *     caller laying them inline, as a Conversions add row does
     */
    List<List<ExpressionLines.Run>> inlineRuns() {
        List<List<ExpressionLines.Run>> runs = new ArrayList<>();
        for (int index = 0; index < amounts.size(); index++) {
            List<ExpressionLines.Run> cell = new ArrayList<>();
            if (reduced && index == 0) {
                cell.add(OPEN);
            }
            cell.addAll(cellRuns(amounts.get(index)));
            if (reduced && index == amounts.size() - 1) {
                cell.add(CLOSE);
            }
            runs.add(cell);
        }
        return runs;
    }

    // A cell's icon, where it carries a type, then its amount.
    private static List<ExpressionLines.Run> cellRuns(TooltipLine.GooAmount amount) {
        ExpressionLines.Run digits = ExpressionLines.amount(amount.amount());
        return amount.type().isEmpty() ? List.of(digits)
                : List.of(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, NO_TYPE, amount.type()), digits);
    }

    private int rows() {
        return (amounts.size() + columns - 1) / columns;
    }

    private int iconSize(TooltipLine.GooAmount amount) {
        return amount.type().isEmpty() ? 0 : sizeOf.applyAsInt(amount.type());
    }

    private int cellWidth(Font font, TooltipLine.GooAmount amount) {
        int icon = iconSize(amount);
        return (icon == 0 ? 0 : icon + ICON_GAP - 1) + SmallDigits.advance(font, amount.amount());
    }

    // The widest cell in the grid, which every column takes.
    private int columnWidth(Font font) {
        return amounts.stream().mapToInt(amount -> cellWidth(font, amount)).max().orElse(0);
    }

    private int rowHeight() {
        int icon = amounts.stream().mapToInt(this::iconSize).max().orElse(0);
        return Math.max(icon, SmallDigits.GLYPH_HEIGHT) + LINE_GAP;
    }

    private int openWidth(Font font) {
        return reduced ? font.width(OPEN.text()) : 0;
    }

    /**
     * @param font  the font
     * @param index a cell's place in the grid, from 0
     * @return the cell's left edge from the grid's left
     */
    int cellLeft(Font font, int index) {
        return openWidth(font) + index % columns * (columnWidth(font) + COLUMN_GAP);
    }

    /**
     * @param font the font
     * @return how far below the grid's top a full-size label beside it stands, on its first row
     */
    int textOffset(Font font) {
        return Lettering.full(font).centeredTop(0, rowHeight() - LINE_GAP);
    }

    @Override
    public int getHeight(Font font) {
        return rows() * rowHeight();
    }

    @Override
    public int getWidth(Font font) {
        int close = reduced ? font.width(CLOSE.text()) : 0;
        return openWidth(font) + columns * (columnWidth(font) + COLUMN_GAP) - COLUMN_GAP + close;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        draw(graphics, font, x, y);
    }

    /**
     * Draws the grid with its top-left at the place given.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param x        its left edge
     * @param y        its top
     */
    void draw(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        if (amounts.isEmpty()) {
            return;
        }
        int rowHeight = rowHeight();
        int textTop = y + textOffset(font);
        if (reduced) {
            graphics.text(font, OPEN.text(), x, textTop, Palette.GOLD);
        }
        int lastRight = x;
        for (int index = 0; index < amounts.size(); index++) {
            TooltipLine.GooAmount amount = amounts.get(index);
            int left = x + cellLeft(font, index);
            int top = y + index / columns * rowHeight;
            drawCell(graphics, font, amount, left, top, rowHeight - LINE_GAP);
            lastRight = left + cellWidth(font, amount) - 1;
        }
        if (reduced) {
            int lastTop = y + (amounts.size() - 1) / columns * rowHeight + textOffset(font);
            graphics.text(font, CLOSE.text(), lastRight, lastTop, Palette.GOLD);
        }
    }

    private void drawCell(GuiGraphicsExtractor graphics, Font font, TooltipLine.GooAmount amount, int left, int top,
                          int band) {
        int icon = iconSize(amount);
        if (icon == 0) {
            SmallDigits.draw(graphics, font, amount.amount(), left,
                    top + GooTypeIcons.centered(band, SmallDigits.GLYPH_HEIGHT) + 1, Palette.TEXT);
            return;
        }
        GooTypeIcons.blit(graphics, amount.type(), left, top);
        SmallDigits.drawBesideIcon(graphics, font, amount.amount(), new SmallDigits.Icon(left + icon, top, icon,
                ICON_GAP), Palette.TEXT);
    }
}
