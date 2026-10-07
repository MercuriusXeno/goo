package com.mercuriusxeno.goo.block.gasket;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A waterlogged choral gasket keeps its water ticking through the shared
 * waterlogging idiom and still falls to air when its support goes
 * (decision waterlogging-idiom-lives-once). The block runs its real methods on a
 * mock that skips the constructor, since a unit JVM binds no block registry id.
 */
class ChoralGasketBlockTest {

    private static final BlockPos POS = new BlockPos(4, 5, 6);

    /**
     * The vanilla bootstrap asks FML whether it runs in production, which a test JVM
     * cannot answer, so a stubbed loader answers it while the bootstrap stands the blocks.
     */
    @BeforeAll
    static void standVanillaBlocks() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @Test
    void waterloggedGasketWithItsSupportGoneFallsToAirAndTicksItsWater() {
        ChoralGasketBlock block = mock(ChoralGasketBlock.class, CALLS_REAL_METHODS);
        BlockState state = mock(BlockState.class);
        when(state.getValue(BlockStateProperties.WATERLOGGED)).thenReturn(true);
        LevelReader level = mock(LevelReader.class);
        BlockState below = mock(BlockState.class);
        when(level.getBlockState(POS.below())).thenReturn(below);
        when(below.isFaceSturdy(level, POS.below(), Direction.UP)).thenReturn(false);
        ScheduledTickAccess ticks = mock(ScheduledTickAccess.class);

        BlockState updated = block.updateShape(state, level, ticks, POS, Direction.DOWN, POS.below(), below,
                mock(RandomSource.class));

        assertSame(Blocks.AIR.defaultBlockState(), updated);
        verify(ticks).scheduleTick(eq(POS), eq(Fluids.WATER), anyInt());
    }

    @Test
    void supportedGasketKeepsItsState() {
        ChoralGasketBlock block = mock(ChoralGasketBlock.class, CALLS_REAL_METHODS);
        BlockState state = mock(BlockState.class);
        when(state.getValue(BlockStateProperties.WATERLOGGED)).thenReturn(false);
        LevelReader level = mock(LevelReader.class);
        BlockState below = mock(BlockState.class);
        when(level.getBlockState(POS.below())).thenReturn(below);
        when(below.isFaceSturdy(any(), any(), any())).thenReturn(true);

        BlockState updated = block.updateShape(state, level, mock(ScheduledTickAccess.class), POS, Direction.DOWN,
                POS.below(), below, mock(RandomSource.class));

        assertSame(state, updated);
    }
}
