package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Draws a value tooltip's goo amounts as a grid of ceil(sqrt(count)) columns filled row by row, every column as wide
 * as the widest icon and amount in the tooltip, so the icons align down each column (decision
 * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits). Each cell draws as a
 * {@link GooAmountTooltipComponent} line.
 */
public final class GooAmountGrid implements ClientTooltipComponent {
    // The room between one column's widest amount and the next column's icons.
    private static final int COLUMN_GAP = 4;

    private final List<TooltipLine.GooAmount> amounts;
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
        this.sizeOf = sizeOf;
        this.columns = Math.max(1, (int) Math.ceil(Math.sqrt(this.amounts.size())));
    }

    private int rows() {
        return (amounts.size() + columns - 1) / columns;
    }

    private GooAmountTooltipComponent cell(TooltipLine.GooAmount amount) {
        return new GooAmountTooltipComponent(amount, sizeOf);
    }

    // The widest cell in the tooltip, which every column takes.
    private int cellWidth(Font font) {
        return amounts.stream().mapToInt(amount -> cell(amount).getWidth(font)).max().orElse(0);
    }

    private int cellHeight(Font font) {
        return amounts.stream().mapToInt(amount -> cell(amount).getHeight(font)).max().orElse(0);
    }

    @Override
    public int getHeight(Font font) {
        return rows() * cellHeight(font);
    }

    @Override
    public int getWidth(Font font) {
        return columns * (cellWidth(font) + COLUMN_GAP) - COLUMN_GAP;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int columnWidth = cellWidth(font) + COLUMN_GAP;
        int rowHeight = cellHeight(font);
        for (int index = 0; index < amounts.size(); index++) {
            cell(amounts.get(index)).extractImage(font, x + index % columns * columnWidth,
                    y + index / columns * rowHeight, columnWidth, rowHeight, graphics);
        }
    }
}
