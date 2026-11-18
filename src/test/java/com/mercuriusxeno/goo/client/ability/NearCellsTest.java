package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The client's index of a goo's cells, Shatter's here, scans the sections near the camera on its
 * first tick in a level, so the shards over cells already standing draw, and
 * again each time its scan interval has run; a cell counts a living thing
 * within 4 blocks as near, and its shards' shown share climbs to full over
 * the fade while one is near and falls back to none once none is.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 */
class NearCellsTest {

    private static final long LIVE_GAME_TIME = 48_213L;

    @Test
    void theFirstTickInALevelScansTheChunksNearTheCamera() {
        Level level = mock(Level.class);

        new NearCells<>(CrystalShatterVisual::isShatter, ShatterCellShards::layoutFromSeed, () -> 7L).rescanIfDue(level, new Vec3(0, 64, 0), LIVE_GAME_TIME);

        verify(level, atLeastOnce()).getChunk(anyInt(), anyInt(), any(ChunkStatus.class), anyBoolean());
    }

    @Test
    void aScanIsDueOnceItsIntervalHasRunAndNotBefore() {
        assertFalse(NearCells.isDue(LIVE_GAME_TIME, LIVE_GAME_TIME + NearCells.SCAN_TICKS - 1));
        assertTrue(NearCells.isDue(LIVE_GAME_TIME, LIVE_GAME_TIME + NearCells.SCAN_TICKS));
        assertTrue(NearCells.isDue(LIVE_GAME_TIME, LIVE_GAME_TIME - 1), "a clock run backward rescans");
    }

    @Test
    void aLivingThingWithinFourBlocksIsNearAndOnePastFourIsNot() {
        BlockPos cell = new BlockPos(4, 64, -9);
        Vec3 center = Vec3.atCenterOf(cell);

        assertTrue(NearCells.isNear(cell, List.of(center.add(3.9, 0, 0))));
        assertFalse(NearCells.isNear(cell, List.of(center.add(0, 0, 4.1))));
        assertFalse(NearCells.isNear(cell, List.of()));
    }

    @Test
    void theShownShareClimbsToFullOverTheFadeWhileNearAndFallsBackOnceNothingIs() {
        float shown = 0f;
        for (int tick = 1; tick < NearCells.FADE_TICKS; tick++) {
            shown = NearCells.shownAfterTick(shown, true);
        }
        assertTrue(shown > 0f && shown < 1f, "one tick short of the fade the share reads " + shown);
        shown = NearCells.shownAfterTick(shown, true);
        assertEquals(1f, shown, 1e-5f);
        assertEquals(1f, NearCells.shownAfterTick(shown, true), 1e-5f);

        for (int tick = 0; tick < NearCells.FADE_TICKS; tick++) {
            shown = NearCells.shownAfterTick(shown, false);
        }
        assertEquals(0f, shown, 1e-5f);
        assertEquals(0f, NearCells.shownAfterTick(shown, false), 1e-5f);
    }
}
