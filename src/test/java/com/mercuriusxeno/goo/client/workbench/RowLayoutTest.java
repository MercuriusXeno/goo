package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the row arithmetic every listing pane draws from: rows holding a sprite step 18px, and a separator stands
 * between consecutive entries and after none of the last (decisions sprites-stay-16px-and-rows-tighten-to-18px,
 * entries-draw-line-separators).
 */
class RowLayoutTest {
    private static final int TOP = 40;
    private static final int ROWS = 4;

    /**
     * Items rows and Groups cards hold a 16px sprite in an 18px row.
     */
    @Test
    void spriteRowsStepEighteen() {
        assertEquals(16, RowLayout.SPRITE_SIZE);
        for (int rowHeight : List.of(ValuesPane.ROW_HEIGHT, GroupsPane.CARD_HEIGHT, GroupsPane.ROW_HEIGHT)) {
            assertEquals(18, rowHeight);
            assertEquals(18, RowLayout.rowTop(TOP, 1, rowHeight) - RowLayout.rowTop(TOP, 0, rowHeight));
            assertEquals(18, RowLayout.rowTop(TOP, 3, rowHeight) - RowLayout.rowTop(TOP, 2, rowHeight));
        }
    }

    /**
     * A separator stands one pixel above every row but the first, so it falls between each pair and after none of
     * the last, whatever the pane's row height: Items, Groups and its cards at 18, a Prefabs block or a Mutators row
     * of its own height.
     *
     * @param rowHeight the pane's row height
     */
    @ParameterizedTest
    @ValueSource(ints = {18, 13, 37})
    void separatorsStandBetweenRowsAndAfterNoneOfTheLast(int rowHeight) {
        List<Integer> separators = new ArrayList<>();
        for (int index = 0; index < ROWS; index++) {
            int rowTop = RowLayout.rowTop(TOP, index, rowHeight);
            OptionalInt separator = RowLayout.separatorAbove(index, rowTop);
            separator.ifPresent(separators::add);
            if (index > 0) {
                int above = RowLayout.rowTop(TOP, index - 1, rowHeight);
                assertTrue(separator.getAsInt() >= above && separator.getAsInt() < rowTop);
            }
        }
        assertEquals(ROWS - 1, separators.size());
        assertTrue(separators.getLast() < RowLayout.rowTop(TOP, ROWS - 1, rowHeight));
    }
}
