package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What each block of a live level is to a player Tartarus sets down there.
 * Reading a block of a chunk not yet loaded loads it, generating it where it
 * was never visited, so the search sees the ground it sets the player on. A
 * block past the world border is no room and no ground.
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 *
 * @param level the level the gate leads to
 */
public record TartarusLevelCells(ServerLevel level) implements TartarusCells {

    /**
     * A level's height under its logical height, so the nether's stops under its bedrock roof.
     *
     * @param level the level
     * @return its span
     */
    public static HeightSpan spanOf(ServerLevel level) {
        return HeightSpan.of(level.getMinY(), level.getLogicalHeight());
    }

    @Override
    public Cell at(BlockPos pos) {
        if (!level.isInsideBuildHeight(pos.getY()) || !level.getWorldBorder().isWithinBounds(pos)) {
            return Cell.OTHER;
        }
        BlockState state = level.getBlockState(pos);
        if (burns(state)) {
            return Cell.BURNING;
        }
        return state.isAir() ? Cell.OPEN : groundOf(state, pos);
    }

    private Cell groundOf(BlockState state, BlockPos pos) {
        return state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP) ? Cell.FLOOR
                : Cell.OTHER;
    }

    private static boolean burns(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || CampfireBlock.isLitCampfire(state);
    }
}
