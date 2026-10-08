package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Melting goo mingles its types by their shares, largest first
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class MingledGooTest {

    /** Beef: 324 vital to 1500 nether. */
    private final MingledGoo beef = MingledGoo.of(Map.of(GooTypes.VITAL, 324, GooTypes.NETHER, 1500));

    @Test
    void theLargestTypeLeads() {
        assertEquals(GooTypes.NETHER, beef.largest());
    }

    @Test
    void patchesPickTypesByShare() {
        assertEquals(GooTypes.NETHER, beef.pick(0.5));
        assertEquals(GooTypes.VITAL, beef.pick(0.9));
    }

    @Test
    void noGooPicksNothing() {
        assertNull(MingledGoo.NONE.pick(0.5));
        assertNull(MingledGoo.of(Map.of()).largest());
    }
}
