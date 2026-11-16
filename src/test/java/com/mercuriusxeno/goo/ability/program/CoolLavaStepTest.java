package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
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
 * Goop's stream cools source lava to magma and leaves flowing lava and lava-tagged molten iron as they are
 * (decisions weird-cools-only-source-lava, diagnose-and-correct-molten-iron-moving-you-as-open-air-does).
 */
class CoolLavaStepTest {

    private static final BlockPos SOURCE = new BlockPos(0, 0, 0);
    private static final BlockPos FLOWING = new BlockPos(1, 0, 0);
    private final Fluid sourceLava = mock(Fluid.class);

    @Test
    void coolsTheSourceAndLeavesTheFlowingLava() {
        Level level = mock(Level.class);
        FluidState source = fluidHolding(true);
        FluidState flowing = fluidHolding(false);
        when(level.getFluidState(SOURCE)).thenReturn(source);
        when(level.getFluidState(FLOWING)).thenReturn(flowing);
        BlockState magma = mock(BlockState.class);

        CoolLavaStep.coolSourceLava(level, List.of(SOURCE, FLOWING), sourceLava, magma);

        verify(level).setBlock(SOURCE, magma, Block.UPDATE_ALL);
        verify(level, never()).setBlock(eq(FLOWING), any(), anyInt());
    }

    @Test
    void leavesAMoltenIronSourceThatCarriesTheLavaTag() {
        Level level = mock(Level.class);
        FluidState moltenIron = mock(FluidState.class);
        when(moltenIron.is(FluidTags.LAVA)).thenReturn(true);
        when(moltenIron.isSource()).thenReturn(true);
        when(level.getFluidState(SOURCE)).thenReturn(moltenIron);

        CoolLavaStep.coolSourceLava(level, List.of(SOURCE), sourceLava, mock(BlockState.class));

        verify(level, never()).setBlock(eq(SOURCE), any(), anyInt());
    }

    private FluidState fluidHolding(boolean holdsSourceLava) {
        FluidState fluid = mock(FluidState.class);
        when(fluid.is(FluidTags.LAVA)).thenReturn(true);
        when(fluid.is(sourceLava)).thenReturn(holdsSourceLava);
        return fluid;
    }
}
