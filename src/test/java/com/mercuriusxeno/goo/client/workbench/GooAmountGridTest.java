package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * Tests that a value tooltip's goo amounts lay in ceil(sqrt(count)) columns and a row's in as many columns as its
 * width holds, row by row, every column as wide as the widest amount asks so the icons align down each column, on
 * mocked graphics (decisions tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits,
 * row-amount-grid-fills-the-width-and-wraps-past-it).
 */
class GooAmountGridTest {
    private static final List<String> TYPES = List.of("metal", "pulse", "rock", "jelly", "leaf", "blaze", "weird",
            "zoo", "vital");

    private static GooAmountGrid grid(int count, String widest) {
        List<TooltipLine.GooAmount> amounts = IntStream.range(0, count).mapToObj(index -> new TooltipLine.GooAmount(
                TYPES.get(index), index == 0 ? widest : "3")).toList();
        return new GooAmountGrid(new TooltipLine.GooAmounts(amounts), type -> 11);
    }

    // Each icon's x, keyed by its y, in drawing order.
    private static Map<Integer, List<Integer>> iconXsByRow(GooAmountGrid grid) {
        DrawnFrame frame = new DrawnFrame();
        grid.extractImage(DrawnFrame.font(), 0, 0, 400, 200, frame.graphics);
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeast(0)).blit(any(), any(), xs.capture(), ys.capture(), anyFloat(), anyFloat(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        return IntStream.range(0, xs.getAllValues().size()).boxed().collect(Collectors.groupingBy(
                index -> ys.getAllValues().get(index), TreeMap::new,
                Collectors.mapping(index -> xs.getAllValues().get(index), Collectors.toList())));
    }

    @Test
    void fiveTypesLayThreeColumnsByTwoRows() {
        Map<Integer, List<Integer>> rows = iconXsByRow(grid(5, "3"));
        assertEquals(2, rows.size(), rows.toString());
        assertEquals(List.of(3, 2), rows.values().stream().map(List::size).toList());
    }

    @Test
    void nineTypesLayThreeByThree() {
        Map<Integer, List<Integer>> rows = iconXsByRow(grid(9, "3"));
        assertEquals(List.of(3, 3, 3), rows.values().stream().map(List::size).toList(), rows.toString());
    }

    // A run of five three-amount cells a row lays, width-bound.
    private static GooAmountGrid rowGrid(boolean reduced) {
        List<TooltipLine.GooAmount> amounts = TYPES.subList(0, 5).stream()
                .map(type -> new TooltipLine.GooAmount(type, "3")).toList();
        return GooAmountGrid.inRow(new TooltipLine.GooAmounts(amounts, reduced), type -> 11);
    }

    // The room from one cell's left to the next column's, read off a grid with two columns.
    private static int pitch() {
        return grid(4, "3").cellLeft(DrawnFrame.font(), 1) - grid(4, "3").cellLeft(DrawnFrame.font(), 0);
    }

    /**
     * Decision row-amount-grid-fills-the-width-and-wraps-past-it: five cells in a width holding exactly five lay one
     * line of five, a column apart, one line high.
     */
    @Test
    void rowGridLaysFiveInOneLineWhereTheWidthHoldsFive() {
        Font font = DrawnFrame.font();
        int pitch = pitch();
        GooAmountGrid laid = rowGrid(false).within(font, 5 * pitch - GooAmountGrid.COLUMN_GAP);
        Map<Integer, List<Integer>> rows = iconXsByRow(laid);
        assertEquals(List.of(List.of(0, pitch, 2 * pitch, 3 * pitch, 4 * pitch)), List.copyOf(rows.values()),
                rows.toString());
        assertEquals(grid(1, "3").getHeight(font), laid.getHeight(font));
    }

    /**
     * Decision row-amount-grid-fills-the-width-and-wraps-past-it: five cells in a width one short of holding four lay
     * three then two, and the grid stands two lines high.
     */
    @Test
    void rowGridWrapsThreeThenTwoWhereTheWidthHoldsThree() {
        Font font = DrawnFrame.font();
        int pitch = pitch();
        GooAmountGrid laid = rowGrid(false).within(font, 4 * pitch - GooAmountGrid.COLUMN_GAP - 1);
        Map<Integer, List<Integer>> rows = iconXsByRow(laid);
        assertEquals(List.of(List.of(0, pitch, 2 * pitch), List.of(0, pitch)), List.copyOf(rows.values()),
                rows.toString());
        assertEquals(2 * grid(1, "3").getHeight(font), laid.getHeight(font));
    }

    /**
     * A reduced row grid's parens take their room from the width: five cells and their parens need more than five
     * cells alone, so the width holding five cells alone wraps them.
     */
    @Test
    void rowGridCountsItsParensAgainstTheWidth() {
        Font font = DrawnFrame.font();
        int pitch = pitch();
        int fiveCells = 5 * pitch - GooAmountGrid.COLUMN_GAP;
        int parens = font.width(GooAmountGrid.OPEN.text()) + font.width(GooAmountGrid.CLOSE.text());
        assertEquals(List.of(4, 1), iconXsByRow(rowGrid(true).within(font, fiveCells)).values().stream()
                .map(List::size).toList());
        assertEquals(List.of(5), iconXsByRow(rowGrid(true).within(font, fiveCells + parens)).values().stream()
                .map(List::size).toList());
    }

    /**
     * Decision row-amount-grid-fills-the-width-and-wraps-past-it: a row's column is as wide as its own widest cell, so
     * a short amount after a long one stands one column gap from the next icon, as opulent's 6 before metal's icon.
     */
    @Test
    void rowColumnTakesItsOwnWidestCell() {
        Font font = DrawnFrame.font();
        List<TooltipLine.GooAmount> amounts = List.of(new TooltipLine.GooAmount("metal", "12960"),
                new TooltipLine.GooAmount("pulse", "54"), new TooltipLine.GooAmount("rock", "36"));
        GooAmountGrid laid = GooAmountGrid.inRow(new TooltipLine.GooAmounts(amounts), type -> 11).within(font, 400);
        List<Integer> icons = iconXsByRow(laid).values().iterator().next();
        int shortCell = 11 + 1 + SmallDigits.advance(font, "54");
        assertEquals(icons.get(1) + shortCell + GooAmountGrid.COLUMN_GAP, icons.get(2), icons.toString());
    }

    /**
     * A wrapped row's cells still stand under the cells above them, each column as wide as its own widest cell.
     */
    @Test
    void wrappedRowAlignsEachColumnOnItsOwnWidestCell() {
        Font font = DrawnFrame.font();
        List<TooltipLine.GooAmount> amounts = List.of(new TooltipLine.GooAmount("metal", "3"),
                new TooltipLine.GooAmount("pulse", "3"), new TooltipLine.GooAmount("rock", "12960"),
                new TooltipLine.GooAmount("jelly", "3"));
        int wide = 11 + 1 + SmallDigits.advance(font, "12960");
        int narrow = 11 + 1 + SmallDigits.advance(font, "3");
        List<List<Integer>> rows = List.copyOf(iconXsByRow(GooAmountGrid.inRow(new TooltipLine.GooAmounts(amounts),
                type -> 11).within(font, wide + GooAmountGrid.COLUMN_GAP + narrow)).values());
        assertEquals(List.of(List.of(0, wide + GooAmountGrid.COLUMN_GAP), List.of(0, wide + GooAmountGrid.COLUMN_GAP)),
                rows, "two columns, the second line's icons under the first's");
    }

    @Test
    void iconsAlignDownEveryColumn() {
        List<List<Integer>> rows = List.copyOf(iconXsByRow(grid(9, "3")).values());
        assertEquals(rows.get(0), rows.get(1));
        assertEquals(rows.get(0), rows.get(2));
    }

    @Test
    void columnsWidenWithTheWidestAmount() {
        Font font = DrawnFrame.font();
        List<Integer> narrow = iconXsByRow(grid(4, "3")).values().iterator().next();
        List<Integer> wide = iconXsByRow(grid(4, "12345")).values().iterator().next();
        assertTrue(wide.get(1) - wide.get(0) > narrow.get(1) - narrow.get(0), narrow + " vs " + wide);
        assertTrue(grid(4, "12345").getWidth(font) > grid(4, "3").getWidth(font));
    }
}
