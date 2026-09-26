package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Render state snapshot for the hub BER. Holds one {@link SlotState}
 * per pipe slot plus the shared animation tick.
 */
public class HubRenderState extends BlockEntityRenderState {

    /** Canister centers in block coords (XZ), indexed by slot. */
    static final float[][] SLOT_CENTERS = {
        { 8f / 16f,  2f / 16f},   // slot 0 (N)
        {13f / 16f,  3f / 16f},   // slot 1 (NE)
        {14f / 16f,  8f / 16f},   // slot 2 (E)
        {13f / 16f, 13f / 16f},   // slot 3 (SE)
        { 8f / 16f, 14f / 16f},   // slot 4 (S)
        { 3f / 16f, 13f / 16f},   // slot 5 (SW)
        { 2f / 16f,  8f / 16f},   // slot 6 (W)
        { 3f / 16f,  3f / 16f},   // slot 7 (NW)
    };

    /** A hub canister hangs from its pipe (cap top at 14px) to the hub base (cap bottom at 2px). */
    private static final CanisterGeometry CANISTER = CanisterGeometry.at(3f / 16f, 13f / 16f);

    /** Per-slot snapshot, indexed 0..MAX_CANISTERS-1. */
    public final SlotState[] slots = new SlotState[HubBlockEntity.MAX_CANISTERS];

    /** Animation time for sin-wave pulsing. */
    public float animationTime;

    /**
     * @return where a hub canister stands
     */
    public CanisterGeometry canisterGeometry() {
        return CANISTER;
    }

    public HubRenderState() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new SlotState();
        }
    }
}
