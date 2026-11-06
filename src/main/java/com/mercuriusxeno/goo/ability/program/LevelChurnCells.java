package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;

/**
 * Churn's cells in a level. A vein is made of ore. A block entity, a fluid,
 * an unbreakable block, half of a two-block door, plant or bed, and a cell
 * outside the world never move, and ground gives way to a rising vein only
 * where it is a full block carrying nothing that would fall off it, so a
 * churn pops no plant, torch or rail and nothing it moves is lost
 * (decision churn-pulls-a-vein-up-as-a-fluid-chain). A moved block is set
 * without neighbor updates, so nothing pops as the vein rises.
 *
 * @param level the churned level
 */
public record LevelChurnCells(Level level) implements ChurnVein.Cells<BlockState> {

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
    public boolean mineral(BlockPos pos) {
        return level.getBlockState(pos).is(Tags.Blocks.ORES);
    }

    @Override
    public boolean movable(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0 && !isHalfOfAPair(state);
    }

    @Override
    public boolean givesWay(BlockPos pos) {
        return movable(pos) && level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)
                && carriesNothing(pos.above());
    }

    /**
     * Whether a cell holds nothing resting on the cell below: open air, a
     * full block, or the churn's own marker, which holds its place.
     *
     * @param pos the cell above the ground
     * @return true where moving the ground below drops nothing
     */
    private boolean carriesNothing(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.isCollisionShapeFullBlock(level, pos)
                || state.is(GooBlocks.ABILITY_BLOCK.get());
    }

    private static boolean isHalfOfAPair(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART);
    }
}
