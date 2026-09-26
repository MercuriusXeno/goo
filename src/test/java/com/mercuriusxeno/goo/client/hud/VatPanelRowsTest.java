package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the vat HUD's rows: the stack's water reads as a fluid row beside its goo rows
 * (decision diagnose-then-fix-vat-hud-water-row).
 */
class VatPanelRowsTest {

    /** The water a stack holds in the tests, in microblobs. */
    private static final long WATER = 4_000_000L;

    /** A stack holding water paints a see-through fluid row carrying the compact amount text. */
    @Test
    void stackHoldingWaterPaintsWaterRow() {
        List<PanelRow> rows = VatPanelRows.rows(vat(GooContents.EMPTY, WATER));

        PanelRow waterRow = rows.getLast();
        assertEquals(1, rows.size());
        assertTrue(waterRow.seeThrough());
        assertEquals(PanelPainter.waterIcon(), waterRow.icon());
        assertEquals(GooTooltipHandler.formatFluidDisplayCompact(WATER), waterRow.segments().getFirst().text());
    }

    /** A stack holding no water paints no fluid row. */
    @Test
    void stackHoldingNoWaterPaintsNoWaterRow() {
        List<PanelRow> rows = VatPanelRows.rows(vat(GooContents.EMPTY, 0));

        assertTrue(rows.isEmpty());
    }

    /** A stack holding goo and water paints one goo row per type, then the water bucket row the canister draws. */
    @Test
    void stackHoldingGooAndWaterPaintsBoth() {
        GooContents goo = new GooContents(Map.of(GooTypes.ROCK, 1000, GooTypes.BLAZE, 500));
        List<PanelRow> rows = VatPanelRows.rows(vat(goo, WATER));

        List<PanelRow> gooRows = rows.subList(0, rows.size() - 1);
        assertEquals(3, rows.size());
        assertFalse(gooRows.stream().anyMatch(PanelRow::seeThrough));
        assertEquals(PanelPainter.waterIcon(), rows.getLast().icon());
        assertEquals(PanelPainter.waterRow(WATER), rows.getLast());
    }

    /**
     * Builds unlabeled single vat stack data with no gaskets and no upgrade.
     *
     * @param contents the stack's summed goo contents
     * @param water    the stack's summed water
     * @return the stack data
     */
    private static VatStackData vat(GooContents contents, long water) {
        return new VatStackData(contents, water, 0, false, false, null, null, null, 1);
    }
}
