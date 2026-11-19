package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Tests the goo type picker's grid, its hover names and its two clicks, with no Screen
 * (decision goo-type-picker-is-a-seven-by-four-icon-grid).
 */
class GooTypePickerTest {
    private static final List<String> TYPES = GooTypes.BUNDLED.stream().map(GooTypes::id).toList();
    private static final int LEFT = 30;
    private static final int TOP = 50;

    private static int centerX(GooTypePicker.Cell cell) {
        return LEFT + cell.column() * GooTypePicker.CELL + GooTypePicker.CELL / 2;
    }

    private static int centerY(GooTypePicker.Cell cell) {
        return TOP + cell.row() * GooTypePicker.CELL + GooTypePicker.CELL / 2;
    }

    /**
     * The picker centers each type's icon in its cell at the texture's own size, for an 11 and a 12 icon alike
     * (decision goo-type-icons-draw-native-everywhere).
     */
    @Test
    void cellCentersItsIconAtItsTextureSize() {
        for (int size : new int[] {11, 12}) {
            assertEquals((GooTypePicker.CELL - size) / 2, GooTypePicker.iconInset(size));
        }
    }

    /**
     * Every type takes a cell on a grid 7 wide and 4 high, in order, so none needs a scroll.
     */
    @Test
    void everyTypeTakesACellOfASevenByFourGrid() {
        List<GooTypePicker.Cell> cells = new GooTypePicker(TYPES).cells();
        assertEquals(TYPES.size(), cells.size());
        assertEquals(TYPES, cells.stream().map(GooTypePicker.Cell::type).toList());
        assertEquals(6, cells.stream().mapToInt(GooTypePicker.Cell::column).max().orElseThrow());
        assertEquals(3, cells.stream().mapToInt(GooTypePicker.Cell::row).max().orElseThrow());
        assertEquals(new GooTypePicker.Cell(TYPES.get(8), 1, 1), cells.get(8));
    }

    /**
     * While the types, the close cell and the no-goo-type cell overflow 7 by 4, the grid grows a row for them, and
     * holds 4 rows once they fit (operator ruling 2026-11-18, yarn standing beside yore and zoo).
     * decision goo-type-picker-is-a-seven-by-four-icon-grid
     */
    @Test
    void gridGrowsARowWhileItsCellsOverflowSevenByFour() {
        GooTypePicker overflowing = new GooTypePicker(TYPES.subList(0, 27));
        assertEquals(5, overflowing.rows());
        GooTypePicker fitting = new GooTypePicker(TYPES.subList(0, 26));
        assertEquals(GooTypePicker.ROWS, fitting.rows());
    }

    /**
     * One click on the opener opens the grid, a hovered cell names its type, and a click on a cell answers its type
     * and closes the grid.
     */
    @Test
    void openGridFramesInThePopupRole() {
        // Decision one-frame-primitive-draws-every-bordered-box.
        GooTypePicker picker = new GooTypePicker(TYPES);
        picker.open();
        DrawnFrame frame = new DrawnFrame();
        picker.draw(frame.graphics, DrawnFrame.font(), frame.targets, LEFT, TOP, 4, type -> { }, null);
        frame.assertOneFrameIn(Frame.POPUP);
    }

    @Test
    void openClickThenCellClickAnswersTheCellsType() {
        GooTypePicker picker = new GooTypePicker(TYPES);
        AtomicReference<String> picked = new AtomicReference<>();
        ClickTargets targets = new ClickTargets();
        picker.addOpener(targets, 0, 0, 40, 10);
        assertTrue(targets.click(5, 5));
        assertTrue(picker.isOpen());
        targets.clear();
        picker.layTargets(targets, LEFT, TOP, picked::set, null);
        GooTypePicker.Cell cell = picker.cells().get(12);
        assertEquals("goo.type." + cell.type(), ((TranslatableContents) targets.hovered(centerX(cell), centerY(cell))
                .flatMap(GooTypeIcons::tooltipFor).orElseThrow().getContents()).getKey());
        assertTrue(targets.click(centerX(cell), centerY(cell)));
        assertEquals(cell.type(), picked.get());
        assertFalse(picker.isOpen());
    }

    private static GooTypePicker opened(ClickTargets targets, AtomicReference<String> picked,
                                        Runnable nothing) {
        GooTypePicker picker = new GooTypePicker(TYPES);
        picker.open();
        picker.layTargets(targets, LEFT, TOP, picked::set, nothing);
        return picker;
    }

    /**
     * The cell past the last type closes the grid with no pick.
     */
    @Test
    void closeCellClosesThePicker() {
        ClickTargets targets = new ClickTargets();
        AtomicReference<String> picked = new AtomicReference<>();
        GooTypePicker picker = opened(targets, picked, null);
        GooTypePicker.Cell close = picker.closeCell();
        assertEquals(cellAfterTheTypes(GooTypePicker.CLOSE, 0), close);
        assertTrue(targets.click(centerX(close), centerY(close)));
        assertFalse(picker.isOpen());
    }

    /**
     * Where the caller allows no goo type, the cell after close picks nothing, on the row the grid grew for it.
     */
    @Test
    void nothingCellPicksNoType() {
        ClickTargets targets = new ClickTargets();
        AtomicReference<String> picked = new AtomicReference<>();
        AtomicReference<Boolean> chose = new AtomicReference<>(false);
        GooTypePicker picker = opened(targets, picked, () -> chose.set(true));
        GooTypePicker.Cell nothing = cellAfterTheTypes(GooTypePicker.NOTHING, 1);
        assertTrue(targets.click(centerX(nothing), centerY(nothing)));
        assertTrue(chose.get());
        assertFalse(picker.isOpen());
    }

    // The cell standing the given places past the last type, row by row on a grid 7 wide.
    private static GooTypePicker.Cell cellAfterTheTypes(String name, int past) {
        int index = TYPES.size() + past;
        return new GooTypePicker.Cell(name, index % GooTypePicker.COLUMNS, index / GooTypePicker.COLUMNS);
    }

    /**
     * A click outside the grid closes it with no pick, and the click goes no further.
     */
    @Test
    void clickOutsideClosesThePicker() {
        ClickTargets targets = new ClickTargets();
        AtomicReference<String> picked = new AtomicReference<>();
        GooTypePicker picker = opened(targets, picked, null);
        assertTrue(targets.click(5, 5));
        assertFalse(picker.isOpen());
    }

    /**
     * A grid fitted to fewer rows than it fills lays targets for those rows alone, and the wheel turns it down to the
     * last row and no further.
     */
    @Test
    void clampedGridScrollsItsRows() {
        ClickTargets targets = new ClickTargets();
        AtomicReference<String> picked = new AtomicReference<>();
        GooTypePicker picker = new GooTypePicker(TYPES);
        picker.open();
        picker.fitRows(2);
        picker.scroll(-1);
        picker.scroll(-1);
        picker.scroll(-1);
        assertEquals(picker.rows() - 2, picker.firstRow());
        picker.layTargets(targets, LEFT, TOP, picked::set, null);
        GooTypePicker.Cell close = picker.closeCell();
        int shownRow = close.row() - picker.firstRow();
        assertTrue(targets.click(LEFT + close.column() * GooTypePicker.CELL + 1,
                TOP + shownRow * GooTypePicker.CELL + 1));
        assertFalse(picker.isOpen());
    }

    /**
     * A disabled cell draws its icon in the disabled role and still names its type on hover, but a click on it answers
     * no type and leaves the grid open, while its enabled neighbor draws untinted and answers its own
     * (decision builder-disables-the-type-buttons-the-referent-lacks).
     */
    @Test
    void disabledCellDrawsDimmedAndLeavesTheGridOpen() {
        String disabled = TYPES.get(3);
        String enabled = TYPES.get(4);
        GooTypePicker picker = new GooTypePicker(TYPES);
        picker.open(Set.of(), Set.of(disabled));
        DrawnFrame frame = new DrawnFrame();
        AtomicReference<String> picked = new AtomicReference<>();
        picker.draw(frame.graphics, DrawnFrame.font(), frame.targets, LEFT, TOP, GooTypePicker.ROWS, picked::set,
                null);
        verify(frame.graphics).blit(any(), eq(GooTypeIcons.texture(disabled)), anyInt(), anyInt(), anyFloat(),
                anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), eq(Palette.DISABLED));
        verify(frame.graphics).blit(any(), eq(GooTypeIcons.texture(enabled)), anyInt(), anyInt(), anyFloat(),
                anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), eq(Palette.TEXT));
        GooTypePicker.Cell disabledCell = picker.cells().get(3);
        assertEquals(GooTypeIcons.hoverKey(disabled),
                frame.targets.hovered(centerX(disabledCell), centerY(disabledCell)).orElseThrow());
        frame.targets.click(centerX(disabledCell), centerY(disabledCell));
        assertTrue(picker.isOpen());
        GooTypePicker.Cell enabledCell = picker.cells().get(4);
        assertTrue(frame.targets.click(centerX(enabledCell), centerY(enabledCell)));
        assertEquals(enabled, picked.get());
    }

    /**
     * A grid opened with goo types hidden offers every other type and none of those, so a value never picks a type
     * it already holds.
     */
    @Test
    void hiddenTypesTakeNoCell() {
        GooTypePicker picker = new GooTypePicker(TYPES);
        picker.open(Set.of(TYPES.get(0), TYPES.get(2)));
        List<GooTypePicker.Cell> cells = picker.cells();
        assertEquals(TYPES.size() - 2, cells.size());
        assertEquals(new GooTypePicker.Cell(TYPES.get(1), 0, 0), cells.getFirst());
    }
}
