package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A surface host anchors its world actions at the center of the face it
 * names, the floor's top face for a spray's reach, and stands a timed block
 * in the open cell above the floor, standing none where the floor already
 * is one.
 */
class SurfaceHostTest {

    private static final BlockPos FLOOR = new BlockPos(4, 60, -9);
    private static final int PAD_TICKS = 200;

    @Test
    void faceCenterSitsHalfABlockOutOfTheBlockCenter() {
        assertEquals(new Vec3(4.5, 61.0, -8.5), SurfaceHost.faceCenter(FLOOR, Direction.UP));
        assertEquals(new Vec3(5.0, 60.5, -8.5), SurfaceHost.faceCenter(FLOOR, Direction.EAST));
    }

    // weird-bounces-and-softens-harm
    @Test
    void aTimedBlockStandsInTheOpenCellAboveTheFloorForItsTicks() {
        ServerLevel level = mock(ServerLevel.class);
        Block pad = mock(Block.class);
        BlockState padState = mock(BlockState.class);
        when(pad.defaultBlockState()).thenReturn(padState);
        when(level.getBlockState(FLOOR)).thenReturn(mock(BlockState.class));

        new SurfaceHost(level, FLOOR, 0).placeForTicks(pad, PAD_TICKS);

        verify(level).setBlock(FLOOR.above(), padState, Block.UPDATE_ALL);
        verify(level).scheduleTick(FLOOR.above(), pad, PAD_TICKS);
    }

    // weird-bounces-and-softens-harm
    @Test
    void aFloorThatIsTheTimedBlockStacksNoSecond() {
        ServerLevel level = mock(ServerLevel.class);
        Block pad = mock(Block.class);
        BlockState floorState = mock(BlockState.class);
        when(floorState.is(pad)).thenReturn(true);
        when(level.getBlockState(FLOOR)).thenReturn(floorState);

        new SurfaceHost(level, FLOOR, 0).placeForTicks(pad, PAD_TICKS);

        verify(level, never()).setBlock(any(), any(), anyInt());
        verify(level, never()).scheduleTick(any(BlockPos.class), any(Block.class), anyInt());
    }
}
