package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Render state snapshot for the plexer BER: the target item and the block's facing.
 */
public class PlexerRenderState extends BlockEntityRenderState {

    /** Item currently set as the reconstitution target. */
    public ItemStack targetItem = ItemStack.EMPTY;

    /** Block facing direction - determines cutaway orientation. */
    public Direction facing = Direction.SOUTH;
}
