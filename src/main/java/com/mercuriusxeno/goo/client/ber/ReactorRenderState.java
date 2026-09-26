package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the reactor BER. Wraps a single
 * {@link SlotState} for the output canister slot plus reactor-specific
 * block-level fields (facing, crafting, wheel animation).
 */
public class ReactorRenderState extends BlockEntityRenderState {

    /** The output canister stands on the hollow floor (cap bottom at 1px). */
    private static final CanisterGeometry CANISTER = CanisterGeometry.at(2f / 16f, 12f / 16f);

    /** Block facing direction; determines hollow orientation. */
    public Direction facing = Direction.SOUTH;

    /** State of the output canister slot. */
    public final SlotState slot = new SlotState();

    /** True when the reactor is actively crafting (wheels at max speed). */
    public boolean crafting;

    /** Current wheel rotation angle in degrees. */
    public float wheelAngle;

    /** Packed light coords sampled at the lateral neighbor on the
     * model-west wheel side (= world {@code facing.getClockWise()}). */
    public int westWheelLight;

    /** Packed light coords sampled at the lateral neighbor on the
     * model-east wheel side (= world {@code facing.getCounterClockWise()}). */
    public int eastWheelLight;

    /**
     * @return where the output canister stands
     */
    public CanisterGeometry canisterGeometry() {
        return CANISTER;
    }
}
