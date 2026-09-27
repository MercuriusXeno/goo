package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
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
 * It also covers the projected height measure, the column layout and the shrink factor that keep
 * a panel on screen (decision panel-wraps-to-two-columns-then-shrinks).
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
                        CruciblePanelRows.rows(rock500, rock250, List.of()), 84f, 17f),
                // ".500 / .500" row 78 beats the "60s" fuel row 30
                Arguments.of("crucible goo and fuel",
                        CruciblePanelRows.rows(rock500, GooContents.EMPTY, List.of(fuelSixtySeconds)), 84f, 28f),
                // fuel row alone: 12+18=30, plus borders
                Arguments.of("crucible fuel only",
                        CruciblePanelRows.rows(GooContents.EMPTY, GooContents.EMPTY, List.of(fuelSixtySeconds)),
                        36f, 17f));
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
                contents(Map.of(GooTypes.ROCK, reservoirVolume)), GooContents.EMPTY, List.of());
        assertEquals(CRUCIBLE_FLOOR_WIDTH, PanelPainter.measure(rows, SIX_PIXELS_A_CHARACTER).width());
    }

    /**
     * Past 10 blobs a crucible row whose text outgrows the floor measures wider:
     * "1.23K / 1.23K" is 78 after the icon and gap, plus borders.
     */
    @Test
    void crucibleTextWiderThanTheFloorMeasuresWider() {
        List<PanelRow> rows = CruciblePanelRows.rows(
                contents(Map.of(GooTypes.ROCK, 1_234_567)), GooContents.EMPTY, List.of());
        float width = PanelPainter.measure(rows, SIX_PIXELS_A_CHARACTER).width();
        assertEquals(96f, width);
        assertTrue(width > CRUCIBLE_FLOOR_WIDTH);
    }

    /**
     * The projected height measure: a panel's world height over the view
     * frustum's height at its distance (decision panel-wraps-to-two-columns-then-shrinks).
     */
    @Nested
    class ProjectedHeight {
        /** A 1-block panel 1 block away at a 70 degree fov covers 1 / (2 tan 35 degrees). */
        @Test
        void oneBlockAtOneBlockCoversTheFrustumShare() {
            assertEquals(1 / (2 * Math.tan(Math.toRadians(35))),
                    PanelPainter.projectedFraction(1, 1, 70), 1e-9);
        }

        /** Doubling the distance halves the share of the screen the panel covers. */
        @Test
        void doublingTheDistanceHalvesTheFraction() {
            double near = PanelPainter.projectedFraction(1, 1, 70);
            assertEquals(near / 2, PanelPainter.projectedFraction(1, 2, 70), 1e-9);
        }
    }

    /**
     * The column layout: past the 80% cap the rows split into two columns, the
     * first taking the odd row; at or under it they stay one column.
     */
    @Nested
    class ColumnLayout {
        /** A 70 degree vertical fov. */
        private static final double FOV = 70;

        /** Five headers of 24, 12, 36, 6 and 30 pixels: one column is 61 pixels tall. */
        private final List<PanelRow> fiveRows = List.of(
                PanelRow.header("aaaa", 0), PanelRow.header("bb", 0), PanelRow.header("cccccc", 0),
                PanelRow.header("d", 0), PanelRow.header("eeeee", 0));

        /**
         * 61 pixels is 0.95 blocks; half a block away the frustum is 0.70 blocks
         * tall, so one column covers 136% and the rows split 3 and 2.
         */
        @Test
        void rowsPastTheCapSplitIntoTwoColumns() {
            PanelPainter.PanelLayout layout =
                    PanelPainter.layOut(fiveRows, SIX_PIXELS_A_CHARACTER, 0.5, FOV);
            // first column 36 wide, gap 6, second column 30 wide, borders 6; three rows tall
            assertEquals(new PanelPainter.PanelSize(78f, 39f), layout.size());
            assertEquals(List.of(
                    new PanelPainter.RowSpot(0, 3f, 3f),
                    new PanelPainter.RowSpot(0, 3f, 14f),
                    new PanelPainter.RowSpot(0, 3f, 25f),
                    new PanelPainter.RowSpot(1, 45f, 3f),
                    new PanelPainter.RowSpot(1, 45f, 14f)), layout.spots());
        }

        /** Five blocks away one column covers 14%, so the rows stay one column at the measured size. */
        @Test
        void rowsUnderTheCapStayOneColumn() {
            PanelPainter.PanelLayout layout =
                    PanelPainter.layOut(fiveRows, SIX_PIXELS_A_CHARACTER, 5, FOV);
            assertEquals(PanelPainter.measure(fiveRows, SIX_PIXELS_A_CHARACTER), layout.size());
            assertEquals(new PanelPainter.PanelSize(42f, 61f), layout.size());
            for (int i = 0; i < fiveRows.size(); i++) {
                assertEquals(new PanelPainter.RowSpot(0, 3f, 3f + 11f * i), layout.spots().get(i));
            }
        }
    }

    /** The shrink factor: the cap over the fraction past the 80% cap, and never a growth. */
    @Nested
    class ShrinkFactor {
        /** Twice the cap's height shrinks by half. */
        @Test
        void twiceTheCapShrinksByHalf() {
            assertEquals(0.5, PanelPainter.shrinkFactor(1.6), 1e-9);
        }

        /** A panel that fits keeps its natural size. */
        @Test
        void panelUnderTheCapKeepsItsSize() {
            assertEquals(1, PanelPainter.shrinkFactor(0.5));
        }
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
