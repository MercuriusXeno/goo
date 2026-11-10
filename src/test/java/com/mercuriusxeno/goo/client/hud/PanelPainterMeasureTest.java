package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
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
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that PanelPainter measures the canister, vat and crucible panels from the rows each
 * machine supplies (decision one-panel-painter-takes-rows). Text is six pixels a character, so each
 * size reads back by hand: a panel is its widest row plus a 3-pixel border each side wide, and
 * 11 pixels a row plus the borders tall; the goo amounts are one grid block whose cell is a
 * 10-pixel icon, a 2-pixel gap, then its digits at three quarters of the text's width, rounded up
 * (decision one-class-lays-every-huds-goo-values).
 * It also covers the projected height measure, the column layout and the shrink factor that keep
 * a panel on screen (decision panel-wraps-to-two-columns-then-shrinks).
 */
class PanelPainterMeasureTest {

    /** Six pixels a character, a monospace stand-in for the font. */
    private static final ToIntFunction<String> SIX_PIXELS_A_CHARACTER = text -> 6 * text.length();

    /**
     * The table of contents and the size each panel lays out at.
     *
     * @return one argument set per panel: a name, its rows, its width and height
     */
    static Stream<Arguments> panelsAndSizes() {
        GooContents rock500 = contents(Map.of(GooTypes.ROCK, 500));
        GooContents rock250 = contents(Map.of(GooTypes.ROCK, 250));
        Map<ResourceKey<GooTypeDefinition>, Integer> twoTypes = new LinkedHashMap<>();
        twoTypes.put(GooTypes.ROCK, 500);
        twoTypes.put(GooTypes.BLAZE, 250);
        PanelRow fuelSixtySeconds = PanelRow.iconText(Identifier.withDefaultNamespace("fuel"), "60s", 0);
        return Stream.of(
                // label "Tank" 24, "500" cell 12+14=26: 26+6 by 6+2*11
                Arguments.of("canister label goo",
                        CanisterPanelRows.rows("Tank", rock500), 32f, 28f),
                Arguments.of("canister goo only",
                        CanisterPanelRows.rows(null, rock500), 32f, 17f),
                // "Reservoir North" 90 is the widest; one header row
                Arguments.of("canister empty with label",
                        CanisterPanelRows.rows("Reservoir North", GooContents.EMPTY), 96f, 17f),
                // "750 / 33.5M" 66 beats the two-cell grid 26+4+26=56; two headers, the grid's one row and the fill row
                Arguments.of("vat stack with every header",
                        VatPanelRows.rows(vat(contents(twoTypes), "Main", 3)), 72f, 50f),
                // "500 / 33.5M" 66 beats the "500" cell 26; the grid and the fill row
                Arguments.of("vat single goo only",
                        VatPanelRows.rows(vat(rock500, null, 1)), 72f, 28f),
                // the "9.99K / 9.99K" floor, 78 at full size and 59 scaled, after icon and gap: 71+6 by 6+11
                Arguments.of("crucible reservoir and pool",
                        CruciblePanelRows.rows(rock500, rock250, List.of()), 77f, 17f),
                // the floored goo cell 71 beats the "60s" fuel row 30
                Arguments.of("crucible goo and fuel",
                        CruciblePanelRows.rows(rock500, GooContents.EMPTY, List.of(fuelSixtySeconds)), 77f, 28f),
                // fuel row alone: 12+18=30, plus borders
                Arguments.of("crucible fuel only",
                        CruciblePanelRows.rows(GooContents.EMPTY, GooContents.EMPTY, List.of(fuelSixtySeconds)),
                        36f, 17f));
    }

    /**
     * Each panel measures to the width and height its rows lay out at.
     *
     * @param panel  the case name
     * @param rows   the rows the machine supplies
     * @param width  the width the rows lay out at
     * @param height the height the rows lay out at
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("panelsAndSizes")
    void panelMeasuresToItsRowsSize(String panel, List<PanelRow> rows, float width, float height) {
        PanelPainter.PanelSize size = PanelPainter.measure(rows, SIX_PIXELS_A_CHARACTER);
        assertEquals(width, size.width(), panel + " width");
        assertEquals(height, size.height(), panel + " height");
    }

    /**
     * "9.99K / 9.99K" is 59 at the amount scale after the 12 of icon and gap, plus 6 of borders: the
     * crucible floor (decision crucible-panel-floors-width-under-ten-thousand).
     */
    private static final float CRUCIBLE_FLOOR_WIDTH = 77f;

    /**
     * A draining crucible below 10K measures the floor's width whatever
     * the format's length at that amount.
     *
     * @param reservoirVolume the reservoir amount, with an empty pool
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {500, 9_000, 9_900, 9_990})
    void crucibleBelowTenThousandMeasuresTheFloor(int reservoirVolume) {
        List<PanelRow> rows = CruciblePanelRows.rows(
                contents(Map.of(GooTypes.ROCK, reservoirVolume)), GooContents.EMPTY, List.of());
        assertEquals(CRUCIBLE_FLOOR_WIDTH, PanelPainter.measure(rows, SIX_PIXELS_A_CHARACTER).width());
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
     * Builds vat stack data with no gaskets, at a vat's base capacity.
     *
     * @param contents  the stack's summed contents
     * @param label     the targeted vat's label, or null
     * @param stackSize the vats in the stack
     * @return the stack data
     */
    private static VatStackData vat(GooContents contents, String label, int stackSize) {
        return new VatStackData(contents, 0, ContainerCapacity.VAT_BASE, false, false, label, null, null, null, stackSize);
    }

    /**
     * An item model draws only from the side of the panel its text reads from, since the
     * background and text cull from behind (decision all-huds-draw-an-items-model-as-the-inventory-does).
     */
    @Nested
    class FrontFacing {

        /**
         * Builds the pose a panel on a block's north face takes, as PanelPainter orients it:
         * camera-relative, its local +z turned to face north, scaled to pixels with Y down.
         *
         * @param panelZFromCamera the panel's Z less the camera's
         * @return the panel's pose matrix
         */
        private static Matrix4f northFacingPanel(float panelZFromCamera) {
            PoseStack poseStack = new PoseStack();
            poseStack.translate(0, 0, panelZFromCamera);
            poseStack.mulPose(new Quaternionf().rotationY((float) Math.PI));
            poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);
            return poseStack.last().pose();
        }

        /** A camera two blocks north of a north-facing panel stands before its front. */
        @Test
        void cameraBeforeTheFrontSeesIt() {
            assertTrue(PanelPainter.frontFacesCamera(northFacingPanel(2f)));
        }

        /** A camera two blocks south of a north-facing panel stands behind it. */
        @Test
        void cameraBehindThePanelSeesNoFront() {
            assertFalse(PanelPainter.frontFacesCamera(northFacingPanel(-2f)));
        }
    }
}
