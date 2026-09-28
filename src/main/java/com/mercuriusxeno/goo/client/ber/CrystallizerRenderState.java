package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the crystallizer BER: its facing and the quartz
 * cluster growing from its spot.
 */
public class CrystallizerRenderState extends BlockEntityRenderState {

    /** The face the dial sits on. */
    public Direction facing = Direction.SOUTH;

    /** The crystal's growth this frame, eased and lerped on the client, from 0 to 1. */
    public double crystalGrowth;

    /** The growing type's crystal look, or null while nothing grows. */
    public CrystalClusterSubmitter.@Nullable Look crystalLook;
}
