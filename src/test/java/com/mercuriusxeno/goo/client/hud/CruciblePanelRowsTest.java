package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the crucible HUD's per-type total of reservoir and pool, which reads
 * past the int range rather than wrapping (decision diagnose-then-fix-crucible-overflow).
 */
class CruciblePanelRowsTest {

    /** A type full in both the reservoir and the pool reads their long sum. */
    @Test
    void typeFullInBothStoresReadsLongTotal() {
        GooContents reservoir = new GooContents(Map.of(GooTypes.ROCK, 2_000_000_000));
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, 2_000_000_000));
        assertEquals(4_000_000_000L, CruciblePanelRows.totalOf(reservoir, pool, GooTypes.ROCK));
    }
}
