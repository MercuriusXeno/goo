package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.unmake.MeltingBlock;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Swaps a block a stream's unmake works for a {@link MeltingBlock} standing in
 * for it, so its goo copy can sag in place, and reads the block a melting
 * block stands in for. A block holding contents, a chest or a furnace, is never
 * swapped: it melts without the sag.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class BlockMelts {

    private BlockMelts() {
    }

    /**
     * The melting block standing in for a worked block, swapping the block
     * for one on the first tick of its melt and keeping it ticking.
     *
     * @param level the server level
     * @param pos   the worked block
     * @return the melting block's entity, or null for a block that cannot sag
     */
    public static @Nullable MeltingBlockEntity work(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(GooBlocks.MELTING_BLOCK.get())) {
            if (state.isAir() || state.hasBlockEntity()) {
                return null;
            }
            level.setBlock(pos, GooBlocks.MELTING_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof MeltingBlockEntity melting) {
                melting.hold(state);
            }
        }
        if (level.getBlockEntity(pos) instanceof MeltingBlockEntity melting) {
            level.scheduleTick(pos, GooBlocks.MELTING_BLOCK.get(), 1);
            return melting;
        }
        return null;
    }

    /**
     * The block standing at a position as far as its goo goes: the block a
     * melting block stands in for, or the block itself.
     *
     * @param level the server level
     * @param pos   the position
     * @return the block's state
     */
    public static BlockState originalAt(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MeltingBlockEntity melting
                ? melting.original() : level.getBlockState(pos);
    }
}
