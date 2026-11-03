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
 * Tests that a value tooltip's goo amounts lay in ceil(sqrt(count)) columns, row by row, every column as wide as the
 * widest amount asks so the icons align down each column, on mocked graphics (decision
 * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
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
