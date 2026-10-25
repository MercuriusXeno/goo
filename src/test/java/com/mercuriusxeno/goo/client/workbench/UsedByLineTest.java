package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests how many used-by items one line draws before it closes with its "... and N more" link, with no Screen
 * (decision used-by-draws-the-terminus-items-alone).
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
        assertEquals("... and 18 more", UsedByLine.moreLabel(fit.hidden()));
    }

    /**
     * An expanded line draws every item, wrapping as it must.
     */
    @Test
    void expandedLineDrawsEveryItem() {
        assertEquals(new UsedByLine.Fit(20, 0), UsedByLine.expanded(20));
    }
}
