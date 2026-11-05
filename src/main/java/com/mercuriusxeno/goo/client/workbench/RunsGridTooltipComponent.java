package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import java.util.List;

/**
 * Draws cells of runs as a grid of ceil(sqrt(count)) columns filled row by row, every column as wide as the widest
 * cell the tooltip measures, so a long value wraps into rows rather than running on one line; every row past the
 * first stands one indent in (decision tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
 */
public final class RunsGridTooltipComponent implements ClientTooltipComponent {
    // The room between one column's widest cell and the next column.
    private static final int COLUMN_GAP = 6;
    private static final int LINE_GAP = 2;
    private static final int WHITE = 0xFFFFFFFF;

    private final List<List<ExpressionLines.Run>> cells;
    private final int columns;

    /**
     * @param grid the cells the grid lays
     */
    public RunsGridTooltipComponent(TooltipLine.RunsGrid grid) {
        this.cells = grid.cells();
        this.columns = columnsFor(cells.size());
    }

    /**
     * @param count how many cells the grid lays
     * @return its columns, ceil(sqrt(count)) and at least one
     */
    static int columnsFor(int count) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(count)));
    }

    private int rows() {
        return (cells.size() + columns - 1) / columns;
    }

    private int cellWidth(Font font) {
        return cells.stream().mapToInt(cell -> ExpressionLines.width(font, cell)).max().orElse(0);
    }

    private int rowHeight() {
        return cells.stream().mapToInt(ExpressionLines::lineHeight).max().orElse(0) + LINE_GAP;
    }

    // A row past the first stands one indent in.
    private static int rowIndent(int row) {
        return row == 0 ? 0 : ControlFlow.INDENT;
    }

    @Override
    public int getHeight(Font font) {
        return rows() * rowHeight();
    }

    @Override
    public int getWidth(Font font) {
        int widest = cellWidth(font);
        int width = 0;
        for (int row = 0; row < rows(); row++) {
            int shown = Math.min(columns, cells.size() - row * columns);
            width = Math.max(width, rowIndent(row) + shown * (widest + COLUMN_GAP) - COLUMN_GAP);
        }
        return width;
    }

    // A tooltip takes no click, so the runs' hover areas land in a frame of their own.
    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int columnWidth = cellWidth(font) + COLUMN_GAP;
        int rowHeight = rowHeight();
        ExpressionLines.Painter painter = new ExpressionLines.Painter(graphics, null, new ClickTargets(), WHITE, WHITE,
                (variable, linkX, linkY, linkWidth) -> { });
        for (int index = 0; index < cells.size(); index++) {
            int row = index / columns;
            ExpressionLines.draw(font, cells.get(index), x + rowIndent(row) + index % columns * columnWidth,
                    y + row * rowHeight, columnWidth, painter);
        }
    }
}
