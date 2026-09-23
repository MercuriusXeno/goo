package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Static helpers for canister inventory click handling: blob/omniblob insert,
 * empty-cursor drain, and gasket cleanup on placement. The hub item shares the
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
        return insertFromCursor(cursor, cursorAccess,
                (type, volume) -> CanisterItem.addGoo(canister, type, volume));
    }

    // --- Blob/omniblob insert ---

    /**
     * Where a cursor's goo goes: takes a type and a volume and answers the volume it kept.
     */
    @FunctionalInterface
    interface GooSink {
        /**
         * Accepts up to the given volume of one goo type.
         *
         * @param type   the goo type offered
         * @param volume the volume offered, in mB
         * @return the volume accepted
         */
        int accept(ResourceKey<GooTypeDefinition> type, int volume);
    }

    /**
     * Pours a blob or omniblob cursor into a sink and depletes the cursor by what the
     * sink accepted. Any other cursor, or a sink accepting nothing, leaves both untouched.
     *
     * @param cursor       the item stack on the cursor
     * @param cursorAccess access to set the cursor contents
     * @param sink         where the goo goes
     * @return true if any goo was transferred
     */
    static boolean insertFromCursor(ItemStack cursor, SlotAccess cursorAccess, GooSink sink) {
        if (cursor.getItem() instanceof GooBlobItem) {
            return handleBlobInsert(cursor, cursorAccess, sink);
        }
        return cursor.getItem() instanceof GooOmniblobItem
                && handleOmniblobInsert(cursor, cursorAccess, sink);
    }

    /**
     * Transfers blob goo into the sink, shrinking the blob stack by accepted blobs.
     *
     * @param cursor       the blob stack on the cursor
     * @param cursorAccess access to set the cursor contents
     * @param sink         where the goo goes
     * @return true if any goo was transferred
     */
    private static boolean handleBlobInsert(ItemStack cursor, SlotAccess cursorAccess, GooSink sink) {
        ResourceKey<GooTypeDefinition> type = BlobStacks.keyOf(cursor);
        if (type == null) { return false; }
        int accepted = sink.accept(type, BlobStacks.volumeOf(cursor));
        if (accepted <= 0) { return false; }

        cursor.shrink(accepted / BlobStacks.MB_PER_BLOB);
        if (cursor.isEmpty()) { cursorAccess.set(ItemStack.EMPTY); }
        return true;
    }

    /**
     * Transfers omniblob goo into the sink, reducing or clearing the cursor.
     *
     * @param cursor       the omniblob on the cursor
     * @param cursorAccess access to set the cursor contents
     * @param sink         where the goo goes
     * @return true if any goo was transferred
     */
    private static boolean handleOmniblobInsert(ItemStack cursor, SlotAccess cursorAccess, GooSink sink) {
        ResourceKey<GooTypeDefinition> type = BlobStacks.keyOf(cursor);
        if (type == null) { return false; }
        int volume = GooOmniblobItem.getVolume(cursor);
        int accepted = sink.accept(type, volume);
        if (accepted <= 0) { return false; }

        applyOmniblobRemainder(cursor, cursorAccess, volume - accepted);
        return true;
    }

    /**
     * Clears the omniblob cursor or updates its remaining volume.
     *
     * @param cursor       the omniblob item stack
     * @param cursorAccess access to set the cursor contents
     * @param remaining    the remaining volume after transfer
     */
    private static void applyOmniblobRemainder(ItemStack cursor, SlotAccess cursorAccess, int remaining) {
        if (remaining <= 0) {
            cursorAccess.set(ItemStack.EMPTY);
        } else {
            GooOmniblobItem.setVolume(cursor, remaining);
        }
    }

    // --- Drain operations ---

    /**
     * Where a drain's goo comes from: answers its dominant type and gives up goo of a type.
     */
    interface GooSource {
        /**
         * Answers the goo type the drain takes.
         *
         * @return the dominant goo type, or null when empty
         */
        @Nullable ResourceKey<GooTypeDefinition> dominantType();

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
     * Drains up to 64,000 mB of the source's dominant goo type onto the cursor as a blob output.
     *
     * @param cursorAccess access to set the cursor contents
     * @param source       where the goo comes from
     * @return true if any goo was extracted
     */
    static boolean drainToCursor(SlotAccess cursorAccess, GooSource source) {
        ResourceKey<GooTypeDefinition> dominant = source.dominantType();
        if (dominant == null) { return false; }

        int extracted = source.remove(dominant, ContainerCapacity.BLOB_CAP);
        if (extracted <= 0) { return false; }

        cursorAccess.set(BlobStacks.createForOutput(dominant, extracted));
        return true;
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
        java.util.UUID gasketId = holder.getGasketId(GasketRole.RECEIVER);
        if (gasketId != null) {
            GasketInstallation.popGasket(level, pos, gasketId);
            holder.clearGasket(GasketRole.RECEIVER);
        }
    }
}
