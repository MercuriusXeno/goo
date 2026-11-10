package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.plexer.CutawayInteractionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers which plexer the panel follows, and where in the plexer's mouth its panel stands and at what scale. */
class PlexerPanelTargetTest {

    private static final BlockPos PLEXER = new BlockPos(4, 70, -9);
    private static final double EPSILON = 1e-9;
    /** How far past the front face the panel stands, in blocks. */
    private static final double STAND_OFF = 0.01;
    /** The mouth's height, from the plexer's bottom to the cutaway's bottom, in blocks. */
    private static final double MOUTH = 8.0 / 16.0;
    /** A one-row panel's laid height, 17 scaled pixels, in blocks. */
    private static final double SHORT_PANEL = 17 * InWorldHud.PIXEL_SCALE;
    /** A panel laid a whole block tall, twice the mouth. */
    private static final double TALL_PANEL = 1.0;

    @Nested
    class TrackedPos {

        @Test
        void aPlexerHoldingATargetIsFollowed() {
            assertEquals(PLEXER, PlexerPanelTarget.trackedPos(PLEXER, PLEXER::equals));
        }

        @Test
        void aPlexerWithNoTargetPaintsNoPanel() {
            assertNull(PlexerPanelTarget.trackedPos(PLEXER, pos -> false));
        }

        @Test
        void aCrosshairOnNoBlockPaintsNoPanel() {
            assertNull(PlexerPanelTarget.trackedPos(null, pos -> true));
        }
    }

    /**
     * The panel stands on the face the cutaway opens on: taken back to model space through the
     * same turn the cutaway's click test reads, the anchor sits at the model's horizontal middle,
     * a stand-off out past the open side (z 0), so the face never cuts the panel.
     */
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void panelStandsOffTheFaceTheCutawayOpensOn(Direction facing) {
        Vec3 anchor = PlexerPanelTarget.anchor(PLEXER, facing, SHORT_PANEL);
        double localX = anchor.x - PLEXER.getX();
        double localZ = anchor.z - PLEXER.getZ();
        Direction front = PlexerPanelTarget.front(facing);
        double alongFront = (localX - 0.5) * front.getStepX() + (localZ - 0.5) * front.getStepZ();

        assertEquals(0.5, CutawayInteractionHelper.toModelX(facing, localX, localZ), EPSILON);
        assertEquals(0.5 + STAND_OFF, alongFront, EPSILON);
    }

    /**
     * The panel floats in the mouth, the strip from the plexer's bottom to the cutaway's bottom
     * (model y 0 to 8/16): its drawn center at 4/16 and its bottom never under the plexer's, for a
     * panel short enough to stand at its own size and for one tall enough to shrink
     * (decision plexer-panel-floats-in-the-mouth).
     */
    @Nested
    class FloatsInTheMouth {

        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
        void shortPanelCentersInTheMouth(Direction facing) {
            assertCenteredInTheMouth(facing, SHORT_PANEL);
        }

        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
        void tallPanelCentersInTheMouth(Direction facing) {
            assertCenteredInTheMouth(facing, TALL_PANEL);
        }

        private void assertCenteredInTheMouth(Direction facing, double laidHeight) {
            double bottom = PlexerPanelTarget.anchor(PLEXER, facing, laidHeight).y;
            double drawnHeight = laidHeight * PlexerPanelTarget.scale(laidHeight);

            assertEquals(PLEXER.getY() + 4.0 / 16.0, bottom + drawnHeight / 2, EPSILON);
            assertTrue(bottom >= PLEXER.getY() - EPSILON,
                    "The panel's bottom edge stands at " + (bottom - PLEXER.getY()) + " blocks, under the plexer");
        }
    }

    /**
     * The panel shrinks only when its laid height outgrows the mouth, and then to fill its
     * height exactly; a shorter panel draws at its own size (decision plexer-panel-floats-in-the-mouth).
     */
    @Nested
    class ShrinksOnlyPastTheMouth {

        @Test
        void panelAtTheMouthsHeightKeepsItsSize() {
            assertEquals(1, PlexerPanelTarget.scale(MOUTH));
        }

        @Test
        void shortPanelKeepsItsSize() {
            assertEquals(1, PlexerPanelTarget.scale(SHORT_PANEL));
        }

        @Test
        void tallPanelScalesByTheMouthOverItsHeight() {
            assertEquals(MOUTH / TALL_PANEL, PlexerPanelTarget.scale(TALL_PANEL), EPSILON);
        }

        /**
         * A target costing every registered goo type lays its cost grid taller than the mouth, so it
         * shrinks to the mouth's height and no further.
         */
        @Test
        void everyTypeCostShrinksToTheMouth() {
            Map<ResourceKey<GooTypeDefinition>, Integer> everyType = new LinkedHashMap<>();
            for (ResourceKey<GooTypeDefinition> type : GooTypes.order()) {
                everyType.put(type, 1);
            }
            List<PanelRow> rows = PlexerPanelRows.rows(new GooValue(everyType));
            double laid = PanelPainter.measure(rows, text -> 6 * text.length()).height() * InWorldHud.PIXEL_SCALE;

            assertTrue(laid > MOUTH, "The every-type panel lays " + laid + " blocks tall");
            assertEquals(MOUTH, laid * PlexerPanelTarget.scale(laid), EPSILON);
        }
    }
}
