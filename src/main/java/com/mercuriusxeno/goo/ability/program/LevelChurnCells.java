package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Churn's cells in a level. A block entity, a fluid, an unbreakable block,
 * half of a two-block door, plant or bed, and a cell outside the world stay
 * put whole, so nothing a churn moves is copied or lost; a moved block is
 * set without neighbor updates, so nothing pops as the column turns.
 * decision churn-rotates-a-plus-shaped-column
 *
 * @param level the churned level
 */
public record LevelChurnCells(Level level) implements ChurnColumn.Cells<BlockState> {

    private static final int QUIET_MOVE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Override
    public BlockState get(BlockPos pos) {
        return level.getBlockState(pos);
    }

    @Override
    public void set(BlockPos pos, BlockState block) {
        level.setBlock(pos, block, QUIET_MOVE);
    }

    @Override
    public boolean pinned(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || state.getDestroySpeed(level, pos) < 0 || isHalfOfAPair(state);
    }

    private static boolean isHalfOfAPair(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART);
    }
}
