package com.mercuriusxeno.goo.client.knownitems;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grid's scroll clamps its first row to the rows at both ends, moves a row per step, and places
 * its thumb as the visible share of the rows
 * (decision menu-lists-known-items-alphabetically-with-search-and-scroll).
 */
class ScrollModelTest {

    private static final int ROWS = 10;
    private static final int VISIBLE = 4;
    private static final int LAST_FIRST_ROW = ROWS - VISIBLE;
    private static final int TRACK = 80;

    @Nested
    class Clamp {
        @Test
        void firstRowPastTheBottomClampsToTheLastPage() {
            assertEquals(LAST_FIRST_ROW, ScrollModel.of(ROWS, VISIBLE, 99).firstRow());
        }

        @Test
        void firstRowAboveTheTopClampsToZero() {
            assertEquals(0, ScrollModel.of(ROWS, VISIBLE, -3).firstRow());
        }

        @Test
        void stepMovesOneRowAndStopsAtEitherEnd() {
            ScrollModel top = ScrollModel.of(ROWS, VISIBLE, 0);
            assertEquals(1, top.scrolledBy(1).firstRow());
            assertEquals(0, top.scrolledBy(-1).firstRow());
            assertEquals(LAST_FIRST_ROW, ScrollModel.of(ROWS, VISIBLE, LAST_FIRST_ROW).scrolledBy(1).firstRow());
        }

        @Test
        void rowsThatFitDoNotScroll() {
            assertTrue(ScrollModel.of(ROWS, VISIBLE, 0).scrolls());
            assertFalse(ScrollModel.of(VISIBLE, VISIBLE, 0).scrolls());
            assertEquals(0, ScrollModel.of(VISIBLE - 1, VISIBLE, 2).firstRow());
        }
    }

    @Nested
    class Thumb {
        @Test
        void thumbSpansTheVisibleShareOfTheTrack() {
            assertEquals(TRACK * VISIBLE / ROWS, ScrollModel.of(ROWS, VISIBLE, 0).thumbSpan(TRACK));
        }

        @Test
        void thumbNeverDrawsShorterThanItsMinimum() {
            assertEquals(ScrollModel.MIN_THUMB, ScrollModel.of(1000, VISIBLE, 0).thumbSpan(TRACK));
        }

        @Test
        void thumbRunsFromTheTrackTopToItsBottom() {
            int span = TRACK * VISIBLE / ROWS;
            assertEquals(0, ScrollModel.of(ROWS, VISIBLE, 0).thumbOffset(TRACK));
            assertEquals(TRACK - span, ScrollModel.of(ROWS, VISIBLE, LAST_FIRST_ROW).thumbOffset(TRACK));
            assertEquals((TRACK - span) / 2, ScrollModel.of(ROWS, VISIBLE, LAST_FIRST_ROW / 2).thumbOffset(TRACK));
        }

        @Test
        void draggingTheThumbScrollsToTheRowItSitsOn() {
            int span = TRACK * VISIBLE / ROWS;
            ScrollModel top = ScrollModel.of(ROWS, VISIBLE, 0);
            assertEquals(LAST_FIRST_ROW, top.withThumbAt(TRACK - span, TRACK).firstRow());
            assertEquals(LAST_FIRST_ROW / 2, top.withThumbAt((TRACK - span) / 2.0, TRACK).firstRow());
            assertEquals(0, top.withThumbAt(-20, TRACK).firstRow());
        }
    }
}
