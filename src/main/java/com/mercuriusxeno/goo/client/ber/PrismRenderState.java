package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state snapshot for the prism: its baked crystal model, the face it
 * grew from, the size the growing transformation gives it and the combo it holds.
 */
public class PrismRenderState extends BlockEntityRenderState {

    /** The prism's baked model, resolved from its block state. */
    public final BlockModelRenderState crystal = new BlockModelRenderState();

    /** The face the prism grew from, the base it scales about. */
    public Direction facing = Direction.UP;

    /** The prism's size, 0 as its blob lands and 1 once grown. */
    public float scale = 1f;

    /** The id of the ability whose program is the prism's combo, empty for a plain prism. */
    public String combo = "";

    /** How strongly an agitator's beat shows this frame, 0 to 1; 0 for any other prism. */
    public float beat;
}
