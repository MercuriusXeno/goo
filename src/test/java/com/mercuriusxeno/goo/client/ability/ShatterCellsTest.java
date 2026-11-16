package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The client's Shatter cell index scans the sections near the camera on its
 * first tick in a level, so the shards over cells already standing draw, and
 * again each time its scan interval has run.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 */
class ShatterCellsTest {

    private static final long LIVE_GAME_TIME = 48_213L;

    @Test
    void theFirstTickInALevelScansTheChunksNearTheCamera() {
        Level level = mock(Level.class);

        new ShatterCells(() -> 7L).rescanIfDue(level, new Vec3(0, 64, 0), LIVE_GAME_TIME);

        verify(level, atLeastOnce()).getChunk(anyInt(), anyInt(), any(ChunkStatus.class), anyBoolean());
    }

    @Test
    void aScanIsDueOnceItsIntervalHasRunAndNotBefore() {
        assertFalse(ShatterCells.isDue(LIVE_GAME_TIME, LIVE_GAME_TIME + ShatterCells.SCAN_TICKS - 1));
        assertTrue(ShatterCells.isDue(LIVE_GAME_TIME, LIVE_GAME_TIME + ShatterCells.SCAN_TICKS));
        assertTrue(ShatterCells.isDue(LIVE_GAME_TIME, LIVE_GAME_TIME - 1), "a clock run backward rescans");
    }
}
