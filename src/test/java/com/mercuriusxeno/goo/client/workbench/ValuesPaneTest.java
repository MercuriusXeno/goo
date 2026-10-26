package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the Items, Tags and Uncovered set's layout on its own line above the search box, with no Screen
 * (decision items-filters-sit-on-their-own-line-as-one-set).
 */
class ValuesPaneTest {
    private static final int LEFT = 10;
    private static final int TOP = 30;

    /**
     * The three boxes stand on one line, each sharing its inner border with the next, the search box sits one line
     * below them, and the one box filled is the filter the tab holds.
     */
    @Test
    void filtersLayAsOneSetAboveTheSearchBox() {
        ValuesPane.ChipLayout layout = ValuesPane.layChips(List.of("Items", "Tags", "Uncovered 3"), String::length,
                LEFT, TOP, ValuesTab.Filter.TAGS);
        List<ValuesPane.Chip> chips = layout.chips();
        assertEquals(List.of(ValuesTab.Filter.ITEMS, ValuesTab.Filter.TAGS, ValuesTab.Filter.UNCOVERED),
                chips.stream().map(ValuesPane.Chip::filter).toList());
        assertEquals(LEFT, chips.getFirst().left());
        for (int index = 1; index < chips.size(); index++) {
            ValuesPane.Chip before = chips.get(index - 1);
            ValuesPane.Chip chip = chips.get(index);
            assertEquals(TOP, chip.top());
            assertEquals(before.height(), chip.height());
            assertEquals(before.left() + before.width() - 1, chip.left());
        }
        assertEquals(List.of(false, true, false), chips.stream().map(ValuesPane.Chip::filled).toList());
        ValuesPane.Chip first = chips.getFirst();
        assertEquals(first.top() + first.height() + ValuesPane.CHIP_LINE_GAP, layout.searchTop());
    }
}
