package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The goo type picker: a grid 7 icons wide and 4 high over every goo type, each cell named on hover, opened by one
 * click and answering its type on the next, with no scrolling (decision goo-type-picker-is-a-seven-by-four-icon-grid).
 */
final class GooTypePicker {
    /** A cell's side, the icon and its margin. */
    static final int CELL = 18;
    /** The grid's columns. */
    static final int COLUMNS = 7;
    /** The grid's rows. */
    static final int ROWS = 4;
    private static final int ICON_INSET = 1;
    private static final int BACKGROUND = 0xF0101010;
    private static final int BORDER = 0xFF606060;

    private final List<String> types;
    private boolean open;

    /**
     * One type's place on the grid.
     *
     * @param type   the goo type's id
     * @param column its column, from 0
     * @param row    its row, from 0
     */
    record Cell(String type, int column, int row) {
    }

    /**
     * @param types the goo types' ids in the order the grid lays them
     */
    GooTypePicker(List<String> types) {
        this.types = List.copyOf(types);
    }

    /**
     * @return a picker over every goo type this side knows, in their order
     */
    static GooTypePicker overKnownTypes() {
        return new GooTypePicker(GooTypes.order().stream().map(GooTypes::id).toList());
    }

    /**
     * @return each type's cell, row by row, the grid holding at most 7 by 4
     */
    List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int index = 0; index < Math.min(types.size(), COLUMNS * ROWS); index++) {
            cells.add(new Cell(types.get(index), index % COLUMNS, index / COLUMNS));
        }
        return cells;
    }

    /**
     * @return whether the grid stands open
     */
    boolean isOpen() {
        return open;
    }

    /**
     * Opens the list or grid, as its opener's click does.
     */
    void open() {
        open = true;
    }

    /**
     * Closes the grid with no pick.
     */
    void close() {
        open = false;
    }

    /**
     * Leaves the area whose click opens the grid.
     *
     * @param targets the frame's click areas
     * @param x       the area's left edge
     * @param y       the area's top edge
     * @param width   its width
     * @param height  its height
     */
    void addOpener(ClickTargets targets, int x, int y, int width, int height) {
        targets.add(x, y, width, height, () -> open = true);
    }

    /**
     * Leaves each cell's click and hover while the grid stands open; a click closes it and answers the cell's type.
     *
     * @param targets the frame's click areas
     * @param x       the grid's left edge
     * @param y       the grid's top edge
     * @param picked  takes the type picked
     */
    void layTargets(ClickTargets targets, int x, int y, Consumer<String> picked) {
        if (!open) {
            return;
        }
        targets.add(x, y, COLUMNS * CELL, ROWS * CELL, () -> { });
        for (Cell cell : cells()) {
            targets.add(x + cell.column() * CELL, y + cell.row() * CELL, CELL, CELL, () -> {
                open = false;
                picked.accept(cell.type());
            }, GooTypeIcons.hoverKey(cell.type()));
        }
    }

    /**
     * Draws the open grid over what stands under it and leaves its targets.
     *
     * @param graphics the frame's graphics
     * @param targets  the frame's click areas
     * @param x        the grid's left edge
     * @param y        the grid's top edge
     * @param picked   takes the type picked
     */
    void draw(GuiGraphicsExtractor graphics, ClickTargets targets, int x, int y, Consumer<String> picked) {
        if (!open) {
            return;
        }
        graphics.nextStratum();
        graphics.fill(x - 1, y - 1, x + COLUMNS * CELL + 1, y + ROWS * CELL + 1, BORDER);
        graphics.fill(x, y, x + COLUMNS * CELL, y + ROWS * CELL, BACKGROUND);
        for (Cell cell : cells()) {
            GooTypeIcons.blit(graphics, cell.type(), x + cell.column() * CELL + ICON_INSET,
                    y + cell.row() * CELL + ICON_INSET);
        }
        layTargets(targets, x, y, picked);
    }
}
