package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * Every type takes a cell on a grid 7 wide and 4 high, in order, so none needs a scroll.
     */
    @Test
    void everyTypeTakesACellOfASevenByFourGrid() {
        List<GooTypePicker.Cell> cells = new GooTypePicker(TYPES).cells();
        assertEquals(26, cells.size());
        assertEquals(TYPES, cells.stream().map(GooTypePicker.Cell::type).toList());
        assertEquals(6, cells.stream().mapToInt(GooTypePicker.Cell::column).max().orElseThrow());
        assertEquals(3, cells.stream().mapToInt(GooTypePicker.Cell::row).max().orElseThrow());
        assertEquals(new GooTypePicker.Cell(TYPES.get(8), 1, 1), cells.get(8));
    }

    /**
     * One click on the opener opens the grid, a hovered cell names its type, and a click on a cell answers its type
     * and closes the grid.
     */
    @Test
    void openClickThenCellClickAnswersTheCellsType() {
        GooTypePicker picker = new GooTypePicker(TYPES);
        AtomicReference<String> picked = new AtomicReference<>();
        ClickTargets targets = new ClickTargets();
        picker.addOpener(targets, 0, 0, 40, 10);
        assertTrue(targets.click(5, 5));
        assertTrue(picker.isOpen());
        targets.clear();
        picker.layTargets(targets, LEFT, TOP, picked::set);
        GooTypePicker.Cell cell = picker.cells().get(12);
        assertEquals("goo.type." + cell.type(), ((TranslatableContents) targets.hovered(centerX(cell), centerY(cell))
                .flatMap(GooTypeIcons::tooltipFor).orElseThrow().getContents()).getKey());
        assertTrue(targets.click(centerX(cell), centerY(cell)));
        assertEquals(cell.type(), picked.get());
        assertFalse(picker.isOpen());
    }
}
