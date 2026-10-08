package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.ability.MingledGoo;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a melting block draws this frame: the block it stands in for, how far
 * it has melted and the goo it melts into
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
public class MeltingBlockRenderState extends BlockEntityRenderState {

    /** The block the melting block stands in for. */
    public BlockState original = Blocks.AIR.defaultBlockState();
    /** The level, for the block's tint; null before the first extract. */
    public @Nullable BlockAndTintGetter level;
    /** The goo the block melts into. */
    public MingledGoo goo = MingledGoo.NONE;
    /** The share melted, 0 whole to 1 slumped; 0 while the unmake is not heard working it. */
    public float melted;
    /** The game time including the partial tick. */
    public float ticks;
}
