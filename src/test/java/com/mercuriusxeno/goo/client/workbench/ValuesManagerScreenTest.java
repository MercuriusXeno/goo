package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the values manager's title-row layout through its static seam, with no Screen; the screen's margin is 8, its
 * button height 14 and its button gap 2.
 */
class ValuesManagerScreenTest {

    /**
     * The X is a square ending at the right margin and the topbar starts one gap left of it (decision
     * x-in-the-top-right-closes-the-manager).
     */
    @Test
    void closeXEndsAtTheRightMarginAndTheTopbarStandsLeftOfIt() {
        ValuesManagerScreen.TitleRow row = ValuesManagerScreen.titleRowOf(400);
        assertEquals(new ValuesManagerScreen.TitleRow(378, 392, 376), row);
    }
}
