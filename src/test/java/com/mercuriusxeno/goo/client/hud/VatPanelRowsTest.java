package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the vat HUD's rows: the stack's water reads as a fluid row beside its goo rows
 * (decision diagnose-then-fix-vat-hud-water-row), then the total against the capacity
 * the held void grows (decision each-void-mb-adds-n-plus-one-capacity).
 */
class VatPanelRowsTest {

    /** The water a stack holds in the tests. */
    private static final long WATER = 4_000_000L;
    private static final int BLAZE = 500;
    private static final String TANK = "Tank";

    /** A stack holding water paints a see-through fluid row carrying the compact amount text, then its fill. */
    @Test
    void stackHoldingWaterPaintsWaterRow() {
        List<PanelRow> rows = VatPanelRows.rows(vat(GooContents.EMPTY, WATER, ContainerCapacity.VAT_BASE));

        PanelRow waterRow = rows.getFirst();
        assertEquals(2, rows.size());
        assertTrue(waterRow.seeThrough());
        assertEquals(PanelPainter.waterIcon(), waterRow.icon());
        assertEquals("4M", waterRow.segments().getFirst().text());
    }

    /** A stack holding nothing paints no row. */
    @Test
    void stackHoldingNothingPaintsNoRow() {
        List<PanelRow> rows = VatPanelRows.rows(vat(GooContents.EMPTY, 0, ContainerCapacity.VAT_BASE));

        assertTrue(rows.isEmpty());
    }

    /** A stack holding goo and water paints one goo row per type, then the water bucket row the canister draws. */
    @Test
    void stackHoldingGooAndWaterPaintsBoth() {
        GooContents goo = new GooContents(Map.of(GooTypes.ROCK, 1000, GooTypes.BLAZE, 500));
        List<PanelRow> rows = VatPanelRows.rows(vat(goo, WATER, ContainerCapacity.VAT_BASE));

        List<PanelRow> gooRows = rows.subList(0, 2);
        assertEquals(4, rows.size());
        assertFalse(gooRows.stream().anyMatch(PanelRow::seeThrough));
        assertEquals(PanelPainter.waterRow(WATER), rows.get(2));
    }

    /** The last row reads the goo and water together against the capacity the stack's void grows. */
    @Test
    void stackReadsItsTotalAgainstTheCapacityItsVoidGrows() {
        int voidHeld = 8_062;
        int capacity = ContainerCapacity.vatCapacity(voidHeld);
        GooContents goo = new GooContents(Map.of(GooTypes.VOID, voidHeld));
        List<PanelRow> rows = VatPanelRows.rows(vat(goo, WATER, capacity));

        assertEquals(PanelPainter.fillRow(voidHeld + WATER, capacity), rows.getLast());
    }

    /**
     * A labeled vat's panel shows its goo label type, icon and name, under the text
     * label row, and the goo and fill rows stand as they do unlabeled
     * (decision hud-panel-shows-the-label-type).
     */
    @Test
    void aLabeledVatShowsItsLabelTypeUnderTheTextLabel() {
        GooContents goo = new GooContents(Map.of(GooTypes.BLAZE, BLAZE));
        List<PanelRow> rows = VatPanelRows.rows(new VatStackData(goo, 0, ContainerCapacity.VAT_BASE, false, false,
                TANK, GooTypes.BLAZE, null, null, 1));

        assertEquals(List.of(
                PanelPainter.labelRow(TANK),
                PanelPainter.gooLabelRow(GooTypes.BLAZE),
                PanelPainter.gooRow(GooTypes.BLAZE, GooFormat.formatAmount(BLAZE)),
                PanelPainter.fillRow(BLAZE, ContainerCapacity.VAT_BASE)), rows);
    }

    @Test
    void anUnlabeledVatShowsNoLabelTypeRow() {
        GooContents goo = new GooContents(Map.of(GooTypes.BLAZE, BLAZE));
        List<PanelRow> rows = VatPanelRows.rows(new VatStackData(goo, 0, ContainerCapacity.VAT_BASE, false, false,
                TANK, null, null, null, 1));

        assertEquals(List.of(
                PanelPainter.labelRow(TANK),
                PanelPainter.gooRow(GooTypes.BLAZE, GooFormat.formatAmount(BLAZE)),
                PanelPainter.fillRow(BLAZE, ContainerCapacity.VAT_BASE)), rows);
    }

    /**
     * Builds unlabeled single vat stack data with no gaskets.
     *
     * @param contents the stack's summed goo contents
     * @param water    the stack's summed water
     * @param capacity the stack's summed capacity
     * @return the stack data
     */
    private static VatStackData vat(GooContents contents, long water, long capacity) {
        return new VatStackData(contents, water, capacity, false, false, null, null, null, null, 1);
    }
}
