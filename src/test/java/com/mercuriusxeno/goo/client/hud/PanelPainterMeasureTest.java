package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that PanelPainter measures the canister, vat and crucible panels to the
 * sizes the three old painters measured for the same contents (decision
 * one-panel-painter-takes-rows). Text is six pixels a character, so each
 * recorded size reads back by hand from the old formulas: a panel is its widest
 * row plus a 3-pixel border each side wide, and 11 pixels a row plus the
 * borders tall; a goo row is a 10-pixel icon, a 2-pixel gap, then its text.
 */
class PanelPainterMeasureTest {

    /** Six pixels a character, a monospace stand-in for the font. */
    private static final ToIntFunction<String> SIX_PIXELS_A_CHARACTER = text -> 6 * text.length();

    /**
     * The table of contents and the size each old painter recorded for it.
     *
     * @return one argument set per panel: a name, its rows, the old width and height
     */
    static Stream<Arguments> panelsAndOldSizes() {
        GooContents rock500 = contents(Map.of(GooTypes.ROCK, 500));
        GooContents rock250 = contents(Map.of(GooTypes.ROCK, 250));
        Map<ResourceKey<GooTypeDefinition>, Integer> twoTypes = new LinkedHashMap<>();
        twoTypes.put(GooTypes.ROCK, 500);
        twoTypes.put(GooTypes.BLAZE, 250);
        PanelRow fuelSixtySeconds = PanelRow.iconText(Identifier.withDefaultNamespace("fuel"), "60s", 0);
        return Stream.of(
                // label "Tank" 24, "Lv 2" 24, ".500" row 12+24=36: 36+6 by 6+3*11
                Arguments.of("canister label upgrade goo",
                        CanisterPanelRows.rows("Tank", 2, rock500), 42f, 39f),
                Arguments.of("canister goo only",
                        CanisterPanelRows.rows(null, 0, rock500), 42f, 17f),
                // "Reservoir North" 90 is the widest; two header rows
                Arguments.of("canister empty with label and upgrade",
                        CanisterPanelRows.rows("Reservoir North", 3, GooContents.EMPTY), 96f, 28f),
                // "Stack: 3" 48 is the widest; three headers and two goo rows
                Arguments.of("vat stack with every header",
                        VatPanelRows.rows(vat(contents(twoTypes), 1, "Main", 3)), 54f, 61f),
                Arguments.of("vat single goo only",
                        VatPanelRows.rows(vat(rock500, 0, null, 1)), 42f, 17f),
                // ".500 / .750" 66 after icon and gap: 78+6 by 6+11
                Arguments.of("crucible reservoir and pool",
                        CruciblePanelRows.rows(rock500, rock250, null), 84f, 17f),
                // ".500 / .500" row 78 beats the "60s" fuel row 30
                Arguments.of("crucible goo and fuel",
                        CruciblePanelRows.rows(rock500, GooContents.EMPTY, fuelSixtySeconds), 84f, 28f),
                // fuel row alone: 12+18=30, plus borders
                Arguments.of("crucible fuel only",
                        CruciblePanelRows.rows(GooContents.EMPTY, GooContents.EMPTY, fuelSixtySeconds), 36f, 17f));
    }

    /**
     * Each panel measures to the old painter's width and height.
     *
     * @param panel     the case name
     * @param rows      the rows the machine supplies
     * @param oldWidth  the width the old painter measured
     * @param oldHeight the height the old painter measured
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("panelsAndOldSizes")
    void panelMeasuresToTheOldPaintersSize(String panel, List<PanelRow> rows, float oldWidth, float oldHeight) {
        PanelPainter.PanelSize size = PanelPainter.measure(rows, SIX_PIXELS_A_CHARACTER);
        assertEquals(oldWidth, size.width(), panel + " width");
        assertEquals(oldHeight, size.height(), panel + " height");
    }

    /**
     * "9.99 / 9.99" is 66 after the 12 of icon and gap, plus 6 of borders: the
     * crucible floor (decision crucible-panel-floors-width-under-ten-blobs).
     */
    private static final float CRUCIBLE_FLOOR_WIDTH = 84f;

    /**
     * A draining crucible below 10 blobs measures the floor's width whatever
     * the compact format's length at that volume.
     *
     * @param reservoirVolume the reservoir volume in mB, with an empty pool
     */
    @ParameterizedTest(name = "{0} mB")
    @ValueSource(ints = {500, 9_000, 9_900, 9_990})
    void crucibleBelowTenBlobsMeasuresTheFloor(int reservoirVolume) {
        List<PanelRow> rows = CruciblePanelRows.rows(
                contents(Map.of(GooTypes.ROCK, reservoirVolume)), GooContents.EMPTY, null);
        assertEquals(CRUCIBLE_FLOOR_WIDTH, PanelPainter.measure(rows, SIX_PIXELS_A_CHARACTER).width());
    }

    /**
     * Past 10 blobs a crucible row whose text outgrows the floor measures wider:
     * "1.23K / 1.23K" is 78 after the icon and gap, plus borders.
     */
    @Test
    void crucibleTextWiderThanTheFloorMeasuresWider() {
        List<PanelRow> rows = CruciblePanelRows.rows(
                contents(Map.of(GooTypes.ROCK, 1_234_567)), GooContents.EMPTY, null);
        float width = PanelPainter.measure(rows, SIX_PIXELS_A_CHARACTER).width();
        assertEquals(96f, width);
        assertTrue(width > CRUCIBLE_FLOOR_WIDTH);
    }

    /**
     * Wraps a type map as goo contents.
     *
     * @param amounts the per-type amounts
     * @return the contents
     */
    private static GooContents contents(Map<ResourceKey<GooTypeDefinition>, Integer> amounts) {
        return new GooContents(amounts);
    }

    /**
     * Builds vat stack data with no gaskets.
     *
     * @param contents    the stack's summed contents
     * @param compression the targeted vat's compression level
     * @param label       the targeted vat's label, or null
     * @param stackSize   the vats in the stack
     * @return the stack data
     */
    private static VatStackData vat(GooContents contents, int compression, String label, int stackSize) {
        return new VatStackData(contents, 0, compression, false, false, label, null, null, stackSize);
    }
}
