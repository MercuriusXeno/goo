package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the tap BER. Wraps a single {@link SlotState}
 * for the body slot plus the spigot facing direction.
 */
public class TapRenderState extends BlockEntityRenderState {

    /** The tap's canister stands on the tap body (cap bottom at 4px). */
    private static final CanisterGeometry CANISTER = CanisterGeometry.at(5f / 16f, 15f / 16f);

    /** The tap's facing direction (spigot direction). */
    public Direction facing = Direction.SOUTH;

    /** State of the single body slot. */
    public final SlotState slot = new SlotState();

    /**
     * @return where the tap's canister stands
     */
    public CanisterGeometry canisterGeometry() {
        return CANISTER;
    }
}
