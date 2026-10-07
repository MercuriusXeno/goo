package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * What a statue draws this frame: the mob it was, posed as it stood, in
 * stone (decision petrify-stone-encasement-and-calcify-map).
 */
public class StatueRenderState extends BlockEntityRenderState {

    /** The mob's render state, or null for a statue holding no mob the client can build. */
    public @Nullable EntityRenderState mob;
}
