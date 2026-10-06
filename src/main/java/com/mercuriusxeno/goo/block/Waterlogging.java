package com.mercuriusxeno.goo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * The vanilla SimpleWaterloggedBlock idiom, held once for every goo block carrying
 * the WATERLOGGED property: a waterlogged block answers a water source and keeps
 * its water flowing through a scheduled water tick.
 */
// decision waterlogging-idiom-lives-once
public final class Waterlogging {

    private Waterlogging() {
    }

    /**
     * @param state    the block state, carrying WATERLOGGED
     * @param fallback the fluid state the block answers when dry
     * @return a water source when the state is waterlogged, the fallback otherwise
     */
    public static FluidState fluidState(BlockState state, FluidState fallback) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : fallback;
    }

    /**
     * Schedules a water tick at the block when its state is waterlogged, so water
     * flows into and around it.
     *
     * @param state the block state, carrying WATERLOGGED
     * @param ticks scheduled tick access for fluid updates
     * @param pos   the block position
     * @param level the level reader giving water's tick delay
     */
    public static void scheduleWaterTick(BlockState state, ScheduledTickAccess ticks, BlockPos pos, LevelReader level) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
    }
}
