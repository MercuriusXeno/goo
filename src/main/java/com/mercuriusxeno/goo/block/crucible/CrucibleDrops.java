package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Static helpers for dropping crucible internals (PMI,
 * reservoir goo) when the block is broken.
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
        dropReservoirAsGoo(crucible, level, pos);
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

    /** Drops reservoir contents as one goo per goo type at its exact value, save infinity,
     * which never leaves a holder and is lost with the crucible. The remainders leave with
     * the goo, so the crucible's own item, which collects the ledger after this, carries
     * none of them a second time.
     * decision infinity-sources-whatever-shares-its-vessel
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     * decision every-producer-consumer-and-container-holds-a-remainder-buffer
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private static void dropReservoirAsGoo(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        GooStacks.dropAll(droppedReservoir(crucible.getReservoir()), level, pos);
        crucible.reservoirHandler().loadRemainders(GooRemainders.EMPTY);
    }

    /**
     * The part of a reservoir a broken crucible drops: every type save infinity, its
     * remainder included.
     *
     * @param reservoir what the crucible holds
     * @return the same contents with no infinity
     */
    static GooContents droppedReservoir(GooContents reservoir) {
        return reservoir.withRemoved(GooTypes.INFINITY, reservoir.getVolume(GooTypes.INFINITY))
                .withRemainders(reservoir.remainders().with(GooTypes.INFINITY, GooFraction.ZERO));
    }
}
