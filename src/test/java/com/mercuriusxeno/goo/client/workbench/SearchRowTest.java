package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the one search row every list stands at its top (decision
 * one-search-row-per-list-with-its-create-control-beside-it).
 */
class SearchRowTest {
    private static final int LEFT = 10;
    private static final int TOP = 20;
    private static final int RIGHT = 300;
    private static final int LIST_TOP = TOP + SearchRow.HEIGHT + SearchRow.GAP;

    /**
     * With no control the box spans the row; with one or two it stretches to the first control's left less one gap,
     * the controls standing at the row's right end; the list starts one gap below the box either way.
     */
    @Test
    void boxStretchesToTheControlsAtItsRight() {
        assertEquals(14, SearchRow.HEIGHT);
        assertEquals(new SearchRow.Laid(LEFT, TOP, RIGHT, RIGHT, LIST_TOP), SearchRow.lay(LEFT, TOP, RIGHT, 0));
        int one = 12;
        assertEquals(new SearchRow.Laid(LEFT, TOP, RIGHT - one - SearchRow.GAP, RIGHT - one, LIST_TOP),
                SearchRow.lay(LEFT, TOP, RIGHT, one));
        int two = one + SearchRow.GAP + 30;
        assertEquals(new SearchRow.Laid(LEFT, TOP, RIGHT - two - SearchRow.GAP, RIGHT - two, LIST_TOP),
                SearchRow.lay(LEFT, TOP, RIGHT, two));
    }
}
