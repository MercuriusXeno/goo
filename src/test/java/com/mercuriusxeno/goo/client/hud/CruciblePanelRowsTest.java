package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the crucible HUD's rows: the per-type total of reservoir and pool, which reads
 * past the int range rather than wrapping (decision diagnose-then-fix-crucible-overflow),
 * and the heat row reading heat ticks and fuel goo (decision fuel-goo-heats-per-mb).
 */
class CruciblePanelRowsTest {

    /** A type full in both the reservoir and the pool reads their long sum. */
    @Test
    void typeFullInBothStoresReadsLongTotal() {
        GooContents reservoir = new GooContents(Map.of(GooTypes.ROCK, 2_000_000_000));
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, 2_000_000_000));
        assertEquals(4_000_000_000L, CruciblePanelRows.totalOf(reservoir, pool, GooTypes.ROCK));
    }

    /** A crucible with 12 heat ticks and 50 mB blaze paints a last row carrying both numbers. */
    @Test
    void heatRowCarriesHeatTicksAndFuelGoo() {
        GooContents reservoir = new GooContents(Map.of(GooTypes.BLAZE, 50));
        List<PanelRow> rows = CruciblePanelRows.rows(reservoir, GooContents.EMPTY, CrucibleFuelDisplay.heatRow(12, 50));

        String heatText = rows.getLast().segments().stream()
                .map(PanelRow.TextSegment::text).collect(Collectors.joining());
        assertEquals(2, rows.size());
        assertEquals("12t / .050", heatText);
    }

    /** A crucible holding neither heat nor fuel goo paints no heat row. */
    @Test
    void noHeatNoFuelPaintsNoHeatRow() {
        assertNull(CrucibleFuelDisplay.heatRow(0, 0));
    }
}
