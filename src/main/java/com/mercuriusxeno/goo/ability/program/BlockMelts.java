package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.unmake.MeltingBlock;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Swaps a block liquifying into an Unmake drink for a {@link MeltingBlock}
 * standing in for it, and reads the block a melting block stands in for. A
 * block holding contents gives them up into its goo: its block entity goes
 * before the swap, so nothing spills.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class BlockMelts {

    private BlockMelts() {
    }

    /**
     * Swaps a block for a melting block standing in for it while it streams
     * into a drink, until its siphon is done.
     *
     * @param level the server level
     * @param pos   the block
     * @param end   the game time its siphon is done
     */
    public static void siphon(ServerLevel level, BlockPos pos, long end) {
        BlockState state = level.getBlockState(pos);
        level.removeBlockEntity(pos);
        level.setBlock(pos, GooBlocks.MELTING_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof MeltingBlockEntity melting) {
            melting.hold(state);
            melting.siphonUntil(end);
        }
        level.scheduleTick(pos, GooBlocks.MELTING_BLOCK.get(),
                (int) Math.max(1, end - level.getGameTime() + MeltingBlock.ORPHAN_TICKS));
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
