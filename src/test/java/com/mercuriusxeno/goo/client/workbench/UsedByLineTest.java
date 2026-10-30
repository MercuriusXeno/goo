package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests how many used-by items one line draws before it closes with its ..+n button, with no Screen
 * (decisions used-by-draws-the-terminus-items-alone, more-button-reads-dot-dot-plus-n).
 */
class UsedByLineTest {

    private static final int STEP = 18;

    /**
     * Items that all fit draw whole, with no "more" link.
     */
    @Test
    void itemsThatFitDrawWhole() {
        assertEquals(new UsedByLine.Fit(4, 0), UsedByLine.fit(4, 100, STEP, more -> 50));
    }

    /**
     * Items past the line's width stop short of room for the "more" link, which counts the rest.
     */
    @Test
    void itemsPastTheWidthStopBeforeTheMoreLink() {
        UsedByLine.Fit fit = UsedByLine.fit(20, 100, STEP, more -> 50);
        assertEquals(new UsedByLine.Fit(2, 18), fit);
        assertEquals("..+18", UsedByLine.moreLabel(fit.hidden()));
        assertEquals("..+3", UsedByLine.moreLabel(3));
    }

    /**
     * An expanded line draws every item, wrapping as it must.
     */
    @Test
    void expandedLineDrawsEveryItem() {
        assertEquals(new UsedByLine.Fit(20, 0), UsedByLine.expanded(20));
    }

    /**
     * An expanded list ends on Hide, a collapsed one on its hidden count, and one that fits on nothing
     * (decision expanded-used-by-lists-take-a-hide-button).
     */
    @Test
    void expandedListEndsOnHideAndCollapsedOnItsCount() {
        assertEquals(Optional.of("Hide"), UsedByLine.endLabel(UsedByLine.expanded(20), true));
        assertEquals(Optional.of("..+3"), UsedByLine.endLabel(new UsedByLine.Fit(2, 3), false));
        assertEquals(Optional.empty(), UsedByLine.endLabel(new UsedByLine.Fit(4, 0), false));
    }

    /**
     * Hide stands after the last row's last item, or opens a row of its own where that row has no room for it.
     */
    @Test
    void hideStandsAfterTheLastItem() {
        assertEquals(new UsedByLine.Slot(2 * STEP, 1), UsedByLine.hideSlot(7, 5, STEP, 100, 30));
        assertEquals(new UsedByLine.Slot(0, 2), UsedByLine.hideSlot(10, 5, STEP, 100, 30));
    }
}
