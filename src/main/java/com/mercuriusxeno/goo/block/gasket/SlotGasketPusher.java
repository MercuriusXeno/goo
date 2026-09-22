package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.block.canister.CanisterSlot;
import com.mercuriusxeno.goo.block.canister.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The gasket pusher a slotted canister drives. Every block entity that holds
 * a canister in a slot stands a {@link GasketPusher} on the slot while the
 * canister carries a linked bottom gasket, and drops it otherwise, so the
 * canister block, the hub and the reactor push through one lifecycle.
 */
public final class SlotGasketPusher {

    private SlotGasketPusher() {
    }

    /**
     * Rebuilds the slot's pusher from its bottom gasket state: disposes the
     * one standing, then stands a fresh one when the canister carries a
     * bottom gasket with a partner and the slot has a fluid handler.
     *
     * @param slot   the slot the canister sits in
     * @param owner  the block entity holding the slot, whose level and position own the push
     * @param access the holder's registry access, or null on the client
     */
    public static void rebuild(CanisterSlot slot, BlockEntity owner, @Nullable IGasketRegistryAccess access) {
        slot.disposePusher();
        if (access == null || !needsPusher(slot)) {
            return;
        }
        slot.setPusher(build(slot, owner, access));
    }

    private static boolean needsPusher(CanisterSlot slot) {
        if (slot.handler() == null || slot.isEmpty()) {
            return false;
        }
        CanisterMetadata meta = CanisterItem.getMetadata(slot.canister());
        return meta.bottomGasketId() != null && meta.bottomPartner() != null;
    }

    private static GasketPusher build(CanisterSlot slot, BlockEntity owner, IGasketRegistryAccess access) {
        CanisterSlotFluidHandler handler = slot.handler();
        GasketPusher pusher = new GasketPusher(handler,
                () -> CanisterItem.getMetadata(slot.canister()).bottomGasketId(),
                () -> CanisterItem.getMetadata(slot.canister()).bottomPartner(),
                owner::getLevel, owner::getBlockPos,
                slot::syncHandlerToStack, access);
        pusher.rebuildCache();
        return pusher;
    }
}
