package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the workbench palette's shared roles, the values every pane draws for them (decision
 * one-palette-names-every-color-by-role).
 */
class PaletteTest {
    /**
     * The muted gray is the one every pane drew, the draft dialog's lighter gray gone, and the good green is the one
     * every pane drew, Audit's brighter green gone.
     */
    @Test
    void mutedAndGoodAreTheCommonValues() {
        assertEquals(0xFFA0A0A0, Palette.MUTED);
        assertEquals(0xFF80E080, Palette.GOOD);
    }
}
