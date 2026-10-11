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
    void eachTypeKeepsItsShare() {
        assertEquals(1500f / 1824f, beef.share(0), 1e-5f);
        assertEquals(324f / 1824f, beef.share(1), 1e-5f);
    }

    @Test
    void theSmoothFieldDriftsGentlyAndStaysInRange() {
        double here = MeltMeshNoise.smooth(1.3, 0.2, 0.7, 5);
        double nearby = MeltMeshNoise.smooth(1.31, 0.2, 0.7, 5);
        assertEquals(here, nearby, 0.05);
        for (int step = 0; step < 50; step++) {
            double share = MeltMeshNoise.smooth(step * 0.37, step * 0.11, step * 0.53, 9);
            org.junit.jupiter.api.Assertions.assertTrue(share >= 0 && share <= 1, "step " + step);
        }
    }

    @Test
    void noGooPicksNothing() {
        assertNull(MingledGoo.NONE.pick(0.5));
        assertNull(MingledGoo.of(Map.of()).largest());
    }
}
