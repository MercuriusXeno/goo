package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Covers which plexer the panel follows, and where its panel stands. */
class PlexerPanelTargetTest {

    private static final BlockPos PLEXER = new BlockPos(4, 70, -9);

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

    @Test
    void panelStandsAPixelAboveTheBlocksTopCenter() {
        assertEquals(new Vec3(4.5, 71.0 + 1.0 / 16.0, -8.5), PlexerPanelTarget.anchor(PLEXER));
    }
}
