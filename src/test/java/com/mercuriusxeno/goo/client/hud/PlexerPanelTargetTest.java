package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.plexer.CutawayInteractionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers which plexer the panel follows, and where on the plexer's front its panel stands. */
class PlexerPanelTargetTest {

    private static final BlockPos PLEXER = new BlockPos(4, 70, -9);
    private static final double EPSILON = 1e-9;

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
     * same turn the cutaway's click test reads, the anchor sits on the model's open side
     * (z 0) at its horizontal middle, under the cutaway's floor at a half block.
     */
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void panelStandsOnTheFaceTheCutawayOpensOn(Direction facing) {
        Vec3 anchor = PlexerPanelTarget.anchor(PLEXER, facing);
        double localX = anchor.x - PLEXER.getX();
        double localZ = anchor.z - PLEXER.getZ();

        assertEquals(0.5, CutawayInteractionHelper.toModelX(facing, localX, localZ), EPSILON);
        assertEquals(0.0, CutawayInteractionHelper.toModelZ(facing, localX, localZ), EPSILON);
        assertEquals(PLEXER.getY() + 3.0 / 16.0, anchor.y, EPSILON);
        assertTrue(anchor.y - PLEXER.getY() < 0.5, "The panel's bottom should stand under the cutaway's floor");
    }
}
