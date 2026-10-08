package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * What a melting block draws this frame: how far its goo copy has sagged
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
public class MeltingBlockRenderState extends BlockEntityRenderState {

    /** The share melted, 0 whole to 1 slumped; 0 while the unmake is not heard working it. */
    public float melted;
}
