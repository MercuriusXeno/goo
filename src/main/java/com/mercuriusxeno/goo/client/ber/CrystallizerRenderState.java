package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the crystallizer BER: its facing and the two
 * canisters standing on its top.
 */
public class CrystallizerRenderState extends BlockEntityRenderState {

    /** The canisters stand on the body's top face: caps at 16 px, bodies 17 to 27 px. */
    private static final CanisterGeometry CANISTER = CanisterGeometry.at(17f / 16f, 27f / 16f);

    /** The face the dial sits on. */
    public Direction facing = Direction.SOUTH;

    /** The two canister slots, back left then back right. */
    public final SlotState[] slots = {new SlotState(), new SlotState()};

    /**
     * @return where the canisters stand
     */
    public CanisterGeometry canisterGeometry() {
        return CANISTER;
    }
}
