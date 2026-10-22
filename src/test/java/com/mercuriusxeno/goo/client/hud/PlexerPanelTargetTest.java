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

/** Covers which plexer the panel follows, and where on the plexer's front its panel stands. */
class PlexerPanelTargetTest {

    private static final BlockPos PLEXER = new BlockPos(4, 70, -9);
    private static final double EPSILON = 1e-9;
    /** How far past the front face the panel stands, in blocks. */
    private static final double STAND_OFF = 0.01;

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
        Vec3 anchor = PlexerPanelTarget.anchor(PLEXER, facing);
        double localX = anchor.x - PLEXER.getX();
        double localZ = anchor.z - PLEXER.getZ();
        Direction front = PlexerPanelTarget.front(facing);
        double alongFront = (localX - 0.5) * front.getStepX() + (localZ - 0.5) * front.getStepZ();

        assertEquals(0.5, CutawayInteractionHelper.toModelX(facing, localX, localZ), EPSILON);
        assertEquals(0.5 + STAND_OFF, alongFront, EPSILON);
    }

    /**
     * A target costing every registered goo type reads one row per type under its name, and
     * that tallest panel stands whole above the plexer: its bottom edge just above the plexer's
     * top, its rows stacking upward, so it covers no cutaway and meets no ground.
     */
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void tallestPanelStandsWholeAboveThePlexer(Direction facing) {
        Map<ResourceKey<GooTypeDefinition>, Integer> everyType = new LinkedHashMap<>();
        for (ResourceKey<GooTypeDefinition> type : GooTypes.order()) {
            everyType.put(type, 1);
        }
        List<PanelRow> rows = PlexerPanelRows.rows("Netherite Block", null, new GooValue(everyType));
        PanelPainter.PanelSize size = PanelPainter.measure(rows, text -> 6 * text.length());
        PanelPlacement placement = PlexerPanelTarget.placement(PLEXER, facing, 0f, 1f);
        double bottom = placement.anchor().y - PanelPainter.hangDrop(placement, size) * InWorldHud.PIXEL_SCALE;

        assertEquals(GooTypes.order().size() + 1, rows.size());
        assertTrue(bottom > PLEXER.getY() + 1,
                "The panel's bottom edge stands at " + (bottom - PLEXER.getY()) + " blocks, under the plexer's top");
        assertEquals(placement.anchor().y, bottom, EPSILON, "The panel should grow upward from its anchor");
    }
}
