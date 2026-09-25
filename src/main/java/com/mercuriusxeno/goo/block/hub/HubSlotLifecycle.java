package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.CanisterSlot;
import com.mercuriusxeno.goo.block.gasket.SlotGasketPusher;
import com.mercuriusxeno.goo.block.gasket.SlotGasketRegistration;
import com.mercuriusxeno.goo.item.CanisterItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Static helpers for hub slot lifecycle: handler creation, pusher management,
 * shape computation, and stream snapshotting.
 */
final class HubSlotLifecycle {

    private HubSlotLifecycle() {
    }

    /**
     * Returns true if the given slot can accept a canister insertion.
     *
     * @param be            the hub block entity
     * @param slot          the slot index
     * @param canisterStack the canister item stack
     * @return true if the slot is valid, empty, and the stack is a canister
     */
    static boolean canInsertAt(HubBlockEntity be, int slot, ItemStack canisterStack) {
        return slot >= 0 && slot < HubBlockEntity.MAX_CANISTERS
                && canisterStack.getItem() instanceof CanisterItem
                && be.containerState().slots[slot].isEmpty();
    }

    /**
     * Inserts a canister into the first empty slot.
     *
     * @param be            the hub block entity
     * @param canisterStack the canister item stack
     * @return true if inserted
     */
    static boolean insertCanister(HubBlockEntity be, ItemStack canisterStack) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (canInsertAt(be, i, canisterStack)) {
                return insertCanister(be, i, canisterStack);
            }
        }
        return false;
    }

    /**
     * Inserts a canister into a specific slot.
     *
     * @param be            the hub block entity
     * @param slot          the slot index (0-7)
     * @param canisterStack the canister item stack to insert
     * @return true if inserted
     */
    static boolean insertCanister(HubBlockEntity be, int slot, ItemStack canisterStack) {
        if (!canInsertAt(be, slot, canisterStack)) {
            return false;
        }
        CanisterSlot s = be.containerState().slots[slot];
        s.setCanister(canisterStack.copyWithCount(1));
        s.buildHandler(() -> be.getLevel() != null ? be.getLevel().getGameTime() : 0L);
        rebuildSlotPusher(be, slot);
        BlockEntitySync.invalidateCapabilities(be);
        registerSlotGaskets(be, slot);
        return true;
    }

    /**
     * Removes the canister from a specific slot.
     *
     * @param be   the hub block entity
     * @param slot the slot index (0-7)
     * @return the removed stack, or EMPTY
     */
    static ItemStack removeCanister(HubBlockEntity be, int slot) {
        if (slot < 0 || slot >= HubBlockEntity.MAX_CANISTERS) {
            return ItemStack.EMPTY;
        }
        CanisterSlot s = be.containerState().slots[slot];
        if (s.isEmpty()) {
            return ItemStack.EMPTY;
        }
        s.disposePusher();
        s.syncHandlerToStack();
        ItemStack removed = s.canister().copy();
        deregisterSlotGaskets(be, slot);
        s.clear();
        BlockEntitySync.invalidateCapabilities(be);
        return removed;
    }

    /**
     * Registers the gaskets of every occupied slot's canister at the hub.
     *
     * @param be the hub block entity
     */
    static void registerAllSlotGaskets(HubBlockEntity be) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (!be.containerState().slots[i].isEmpty()) {
                registerSlotGaskets(be, i);
            }
        }
    }

    /**
     * Clears the registry location of every occupied slot's canister gaskets.
     *
     * @param be the hub block entity
     */
    static void deregisterAllSlotGaskets(HubBlockEntity be) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (!be.containerState().slots[i].isEmpty()) {
                deregisterSlotGaskets(be, i);
            }
        }
    }

    private static void registerSlotGaskets(HubBlockEntity be, int slot) {
        SlotGasketRegistration.register(be.gasket().registryAccess(), be.getLevel(), be.getBlockPos(),
                slot, be.getSlotMetadata(slot));
    }

    private static void deregisterSlotGaskets(HubBlockEntity be, int slot) {
        SlotGasketRegistration.deregister(be.gasket().registryAccess(), be.getSlotMetadata(slot));
    }

    /**
     * Rebuilds slot handlers for all occupied slots.
     *
     * @param be the hub block entity
     */
    static void rebuildAllSlotHandlers(HubBlockEntity be) {
        for (CanisterSlot s : be.containerState().slots) {
            s.buildHandler(() -> be.getLevel() != null ? be.getLevel().getGameTime() : 0L);
        }
    }

    /**
     * Rebuilds the pusher for a slot based on its bottom gasket state.
     *
     * @param be   the hub block entity
     * @param slot the slot index
     */
    static void rebuildSlotPusher(HubBlockEntity be, int slot) {
        SlotGasketPusher.rebuild(be.containerState().slots[slot], be, be.gasket().registryAccess());
    }

    /**
     * Rebuilds pushers for all occupied slots.
     *
     * @param be the hub block entity
     */
    static void rebuildAllSlotPushers(HubBlockEntity be) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            rebuildSlotPusher(be, i);
        }
    }

    /**
     * Computes the union of the frame and all occupied slot shapes.
     *
     * @param slots the slot array
     * @return the computed shape
     */
    static VoxelShape computeShape(CanisterSlot[] slots) {
        VoxelShape result = HubBlock.frameShape();
        for (CanisterSlot s : slots) {
            VoxelShape shape = s.shape();
            if (shape != null) {
                result = Shapes.or(result, shape);
            }
        }
        return result;
    }
}
