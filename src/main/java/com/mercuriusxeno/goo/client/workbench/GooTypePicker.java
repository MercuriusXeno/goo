package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The goo type picker: a grid 7 icons wide and 4 high over every goo type, each cell named on hover, opened by one
 * click and answering its type on the next (decision goo-type-picker-is-a-seven-by-four-icon-grid). The cell past the
 * last type closes it with no pick, the cell after that picks no goo type where the caller allows none, and a click
 * outside the grid or Escape closes it, so an opened picker never holds the screen. Opened near the screen's bottom it
 * shows fewer rows and the wheel scrolls them, since it never flips above its opener
 * (decision popups-anchor-under-the-line-that-opened-them).
 */
final class GooTypePicker {
    /** A cell's side, the icon and its margin. */
    static final int CELL = 18;
    /** The grid's columns. */
    static final int COLUMNS = 7;
    /** The grid's rows. */
    static final int ROWS = 4;
    /** The close cell's name. */
    static final String CLOSE = "close";
    /** The no-goo-type cell's name. */
    static final String NOTHING = "nothing";
    private static final int TEXT_NUDGE = 5;
    private static final int GLYPH_INSET = 6;
    // Wider than any GUI, so a click anywhere outside the grid lands on it.
    private static final int EVERYWHERE = 100_000;
    private static final int EVERYWHERE_SPAN = EVERYWHERE + EVERYWHERE;
    private static final int BACKGROUND = 0xF0101010;
    private static final int BORDER = 0xFF606060;
    private static final int GLYPH = 0xFFFF8080;
    private static final String CLOSE_GLYPH = "x";
    private static final String NOTHING_GLYPH = "-";
    private static final String CLOSE_HINT = "Close without picking a goo type";
    private static final String NOTHING_HINT = "No goo type";

    private final List<String> types;
    private Set<String> hidden = Set.of();
    private boolean open;
    private int firstRow;
    private int shownRows = ROWS;

    /**
     * One type's place on the grid.
     *
     * @param type   the goo type's id, or the close or no-goo-type cell's name
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
     * @return each type's cell, row by row, the grid holding at most 7 by 4 less its close cell
     */
    List<Cell> cells() {
        List<String> offered = types.stream().filter(type -> !hidden.contains(type)).toList();
        List<Cell> cells = new ArrayList<>();
        for (int index = 0; index < Math.min(offered.size(), COLUMNS * ROWS - 1); index++) {
            cells.add(cellAt(offered.get(index), index));
        }
        return cells;
    }

    /**
     * @return the cell past the last type, which closes the grid with no pick
     */
    Cell closeCell() {
        return cellAt(CLOSE, cells().size());
    }

    private Cell nothingCell() {
        return cellAt(NOTHING, cells().size() + 1);
    }

    private static Cell cellAt(String name, int index) {
        return new Cell(name, index % COLUMNS, index / COLUMNS);
    }

    /**
     * @return whether the grid stands open
     */
    boolean isOpen() {
        return open;
    }

    /**
     * Opens the grid, as its opener's click does.
     */
    void open() {
        open(Set.of());
    }

    /**
     * Opens the grid without the goo types given, so a value never picks a type it already holds.
     *
     * @param held the goo types the grid leaves out
     */
    void open(Set<String> held) {
        hidden = Set.copyOf(held);
        open = true;
        firstRow = 0;
    }

    /**
     * Turns the rows shown where the grid stands shorter than its rows.
     *
     * @param amount the wheel's turn, positive toward the top
     */
    void scroll(double amount) {
        int lastFirst = Math.max(0, usedRows() - shownRows);
        firstRow = Math.clamp(firstRow - (long) Math.signum(amount), 0, lastFirst);
    }

    /**
     * @param rows how many rows fit under the opener
     */
    void fitRows(int rows) {
        shownRows = Math.clamp(rows, 1, ROWS);
        scroll(0);
    }

    /**
     * @return the first row shown
     */
    int firstRow() {
        return firstRow;
    }

    private int usedRows() {
        return Math.min(ROWS, nothingCell().row() + 1);
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
        targets.add(x, y, width, height, this::open);
    }

    /**
     * Leaves each cell's click and hover while the grid stands open, and below them an area over everything that
     * closes the grid; a cell's click closes it and answers the cell's type.
     *
     * @param targets the frame's click areas
     * @param x       the grid's left edge
     * @param y       the grid's top edge
     * @param picked  takes the type picked
     * @param nothing runs where no goo type is picked, null where the caller allows none
     */
    void layTargets(ClickTargets targets, int x, int y, Consumer<String> picked, @Nullable Runnable nothing) {
        if (!open) {
            return;
        }
        targets.addShield(-EVERYWHERE, -EVERYWHERE, EVERYWHERE_SPAN, EVERYWHERE_SPAN, this::close);
        targets.addShield(x, y, COLUMNS * CELL, shownRows * CELL, () -> { });
        for (Cell cell : cells()) {
            addCellTarget(targets, x, y, cell, () -> picked.accept(cell.type()), GooTypeIcons.hoverKey(cell.type()));
        }
        addCellTarget(targets, x, y, closeCell(), () -> { }, BuilderOverlays.hintKey(CLOSE_HINT));
        if (nothing != null) {
            addCellTarget(targets, x, y, nothingCell(), nothing, BuilderOverlays.hintKey(NOTHING_HINT));
        }
    }

    // A cell scrolled out of the shown rows takes no click; a shown one closes the grid, then answers.
    private void addCellTarget(ClickTargets targets, int x, int y, Cell cell, Runnable answers, String hoverKey) {
        if (shows(cell)) {
            targets.add(cellX(x, cell), cellY(y, cell), CELL, CELL, () -> {
                open = false;
                answers.run();
            }, hoverKey);
        }
    }

    private boolean shows(Cell cell) {
        return cell.row() >= firstRow && cell.row() < firstRow + shownRows;
    }

    /**
     * @param size a type icon's side, its texture's own (decision goo-type-icons-draw-native-everywhere)
     * @return how far in from its cell's corner the icon stands, centered
     */
    static int iconInset(int size) {
        return GooTypeIcons.centered(CELL, size);
    }

    private static int cellX(int x, Cell cell) {
        return x + cell.column() * CELL;
    }

    private int cellY(int y, Cell cell) {
        return y + (cell.row() - firstRow) * CELL;
    }

    /**
     * Draws the open grid over what stands under it and leaves its targets.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param targets  the frame's click areas
     * @param x        the grid's left edge
     * @param y        the grid's top edge
     * @param rows     how many rows fit under its opener, the screen's bottom clamping it
     * @param picked   takes the type picked
     * @param nothing  runs where no goo type is picked, null where the caller allows none
     */
    void draw(GuiGraphicsExtractor graphics, Font font, ClickTargets targets, int x, int y, int rows,
              Consumer<String> picked, @Nullable Runnable nothing) {
        if (!open) {
            return;
        }
        fitRows(rows);
        graphics.nextStratum();
        graphics.fill(x - 1, y - 1, x + COLUMNS * CELL + 1, y + shownRows * CELL + 1, BORDER);
        graphics.fill(x, y, x + COLUMNS * CELL, y + shownRows * CELL, BACKGROUND);
        for (Cell cell : cells()) {
            if (shows(cell)) {
                int inset = iconInset(GooTypeIcons.sizeOf(cell.type()));
                GooTypeIcons.blit(graphics, cell.type(), cellX(x, cell) + inset, cellY(y, cell) + inset);
            }
        }
        drawGlyph(graphics, font, CLOSE_GLYPH, closeCell(), x, y);
        if (nothing != null) {
            drawGlyph(graphics, font, NOTHING_GLYPH, nothingCell(), x, y);
        }
        layTargets(targets, x, y, picked, nothing);
    }

    private void drawGlyph(GuiGraphicsExtractor graphics, Font font, String glyph, Cell cell, int x, int y) {
        if (shows(cell)) {
            graphics.text(font, glyph, cellX(x, cell) + GLYPH_INSET, cellY(y, cell) + TEXT_NUDGE, GLYPH);
        }
    }
}
