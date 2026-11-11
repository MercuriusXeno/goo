package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Goop's stream cools source lava to magma and leaves flowing lava as it is
 * (decision weird-cools-only-source-lava).
 */
class CoolLavaStepTest {

    private static final BlockPos SOURCE = new BlockPos(0, 0, 0);
    private static final BlockPos FLOWING = new BlockPos(1, 0, 0);

    @Test
    void coolsTheSourceAndLeavesTheFlowingLava() {
        Level level = mock(Level.class);
        FluidState source = lava(true);
        FluidState flowing = lava(false);
        when(level.getFluidState(SOURCE)).thenReturn(source);
        when(level.getFluidState(FLOWING)).thenReturn(flowing);
        BlockState magma = mock(BlockState.class);

        CoolLavaStep.coolSourceLava(level, List.of(SOURCE, FLOWING), magma);

        verify(level).setBlock(SOURCE, magma, Block.UPDATE_ALL);
        verify(level, never()).setBlock(eq(FLOWING), any(), anyInt());
    }

    private static FluidState lava(boolean isSource) {
        FluidState fluid = mock(FluidState.class);
        when(fluid.is(FluidTags.LAVA)).thenReturn(true);
        when(fluid.isSource()).thenReturn(isSource);
        return fluid;
    }
}
