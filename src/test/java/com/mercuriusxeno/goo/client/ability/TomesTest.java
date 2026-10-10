package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Enchant's book through its choreography: shut as it starts, wide
 * open while it drinks in glyphs, shut again once it snaps, and faded out by
 * its end.
 */
class TomesTest {

    private static final float TOLERANCE = 1e-6f;

    @Test
    void theBookStartsShut() {
        assertEquals(0f, Tomes.enchantOpenness(0f), TOLERANCE);
    }

    @Test
    void theBookStandsOpenWhileItDrinksGlyphs() {
        assertTrue(Tomes.enchantOpenness(Tomes.SNAP_AT / 2f) > 1f);
    }

    @Test
    void theBookIsShutOnceItSnaps() {
        assertEquals(0f, Tomes.enchantOpenness(Tomes.SNAP_AT), TOLERANCE);
    }

    @Test
    void theBookFadesOutByTheEnd() {
        assertEquals(1f, Tomes.showAlpha(Tomes.SNAP_AT, Tomes.SNAP_AT + 1, Tomes.ENCHANT_TICKS), TOLERANCE);
        assertEquals(0f, Tomes.showAlpha(Tomes.ENCHANT_TICKS, Tomes.SNAP_AT + 1, Tomes.ENCHANT_TICKS), TOLERANCE);
    }
}
