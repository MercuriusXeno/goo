package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.item.BlobStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Static helpers for dropping crucible internals (PMI,
 * reservoir blobs) when the block is broken.
 */
final class CrucibleDrops {

    private CrucibleDrops() { }

    /** Drops all crucible internal state as items when the block is broken.
     *
     * @param level the current level
     * @param pos   the block position
     */
    static void dropCrucibleContents(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return; }

        dropMeltingItem(crucible, level, pos);
        dropReservoirAsBlobs(crucible, level, pos);
    }

    /** Drops the PMI with its remaining goo if present.
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private static void dropMeltingItem(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        ItemStack pmi = crucible.getMeltingItem();
        if (!pmi.isEmpty()) {
            Block.popResource(level, pos, pmi);
        }
    }

    /** Drops reservoir contents as one omniblob per goo type.
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private static void dropReservoirAsBlobs(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        BlobStacks.dropAll(crucible.getReservoir(), level, pos);
    }
}
