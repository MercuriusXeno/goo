package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import java.util.Map;

/**
 * Static helpers for canister inventory click handling: goo insert,
 * empty-cursor drain into the inventory, and gasket cleanup on placement. The hub item shares the
 * cursor insert through {@link #insertFromCursor}.
 * Extracted from CanisterItem to reduce method count.
 */
final class CanisterInventoryHandler {

    private CanisterInventoryHandler() { }

    // --- Primary click dispatch ---

    /**
     * Dispatches primary-click interactions based on cursor item type.
     *
     * @param canister     the canister item stack
     * @param cursor       the cursor item stack
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    static boolean handlePrimaryClick(
            ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        // decision goo-item-carries-its-exact-value-as-a-mixed-remainder: the canister's own ledger banks the part
        return insertFromCursor(cursor, cursorAccess, GooSink.banking(
                canister.getCapability(GooCapabilities.REMAINDER_ITEM, ItemAccess.forStack(canister)),
                (type, volume) -> CanisterItem.addGoo(canister, type, volume)));
    }

    // --- Goo insert ---

    /**
     * Pours a goo cursor into a sink and depletes the cursor by what the
     * sink accepted. Any other cursor, or a sink accepting nothing, leaves both untouched.
     *
     * @param cursor       the item stack on the cursor
     * @param cursorAccess access to set the cursor contents
     * @param sink         where the goo goes
     * @return true if any goo was transferred
     */
    static boolean insertFromCursor(ItemStack cursor, SlotAccess cursorAccess, GooSink sink) {
        return cursor.getItem() instanceof GooItem
                && handleGooInsert(cursor, cursorAccess, sink);
    }

    /**
     * Transfers goo into the sink, reducing or clearing the cursor.
     *
     * @param cursor       the goo on the cursor
     * @param cursorAccess access to set the cursor contents
     * @param sink         where the goo goes
     * @return true if any goo was transferred
     */
    private static boolean handleGooInsert(ItemStack cursor, SlotAccess cursorAccess, GooSink sink) {
        // decision goo-item-carries-its-exact-value-as-a-mixed-remainder: the one pour every goo item runs
        return GooInsert.pourInto(cursor, sink, () -> cursorAccess.set(ItemStack.EMPTY)) > 0;
    }

    // --- Drain operations ---

    /**
     * Where a drain's goo comes from: answers the goo it gives up and removes goo of a type.
     */
    interface GooSource {
        /**
         * Answers the goo this drain takes, per type.
         *
         * @return the volume per type, empty when there is nothing to drain
         */
        Map<ResourceKey<GooTypeDefinition>, Integer> drainable();

        /**
         * Removes up to the given volume of one goo type, capped by what the source holds.
         *
         * @param type   the goo type to remove
         * @param volume the most to remove, in mB
         * @return the volume removed
         */
        int remove(ResourceKey<GooTypeDefinition> type, int volume);
    }

    /**
     * Drains the source whole into the inventory; the cursor is never set, and goo that finds
     * no home stays in the source (decisions vats-and-canisters-drain-whole and
     * drained-goo-fills-carried-containers-first).
     *
     * @param source    where the goo comes from
     * @param depositor where the goo goes
     * @return true if any goo moved
     */
    static boolean drainIntoInventory(GooSource source, GooDeposit.Depositor depositor) {
        return GooDeposit.drainEveryType(source.drainable(), source::remove, depositor);
    }

    // --- Gasket mutual exclusivity ---

    /**
     * Pops any gaskets on the attachment target below when a canister is placed
     * above it. E.g. hub intake gasket pops when a canister is copper-fitted on top.
     *
     * @param level        the current level
     * @param canisterPos  the canister block position
     */
    static void popConflictingGaskets(Level level, BlockPos canisterPos) {
        BlockPos belowPos = canisterPos.below();
        BlockEntity belowBe = level.getBlockEntity(belowPos);
        if (belowBe instanceof IGasketHolder holder) {
            popReceiverGasket(level, belowPos, holder);
        }
    }

    /**
     * Pops the RECEIVER gasket on the top face of the block below, if present.
     *
     * @param level  the current level
     * @param pos    the gasket holder block position
     * @param holder the gasket holder
     */
    private static void popReceiverGasket(Level level, BlockPos pos, IGasketHolder holder) {
        holder.popBlockGasket(GasketRole.RECEIVER);
    }
}
