package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Growth's tick grows each growing plant and throws the bone-meal sparkle at
 * every plant it grows, whatever the random source answers
 * (decision every-ticked-plant-throws-the-sparkle).
 */
class TickPlantsStepTest {

    private static final BlockPos CELL = new BlockPos(3, 64, -7);

    private final ServerLevel level = mock(ServerLevel.class);
    private final BlockState state = mock(BlockState.class);
    private final RandomSource random = mock(RandomSource.class);

    TickPlantsStepTest() {
        when(level.getBlockState(CELL)).thenReturn(state);
        when(random.nextInt(anyInt())).thenReturn(1);
    }

    @Test
    void everyGrownPlantThrowsTheSparkle() {
        when(state.isRandomlyTicking()).thenReturn(true);
        when(state.getBlock()).thenReturn(mock(CropBlock.class));

        TickPlantsStep.tickPlant(level, CELL, random);

        verify(state).randomTick(level, CELL, random);
        verify(level).sendParticles(eq(ParticleTypes.HAPPY_VILLAGER), eq(3.5), eq(64.5), eq(-6.5),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void aBlockThatDoesNotGrowTakesNoTickAndThrowsNoSparkle() {
        when(state.isRandomlyTicking()).thenReturn(false);
        when(state.getBlock()).thenReturn(mock(Block.class));

        TickPlantsStep.tickPlant(level, CELL, random);

        verify(state, never()).randomTick(any(), any(), any());
        verify(level, never()).sendParticles(any(), anyDouble(), anyDouble(), anyDouble(),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
}
