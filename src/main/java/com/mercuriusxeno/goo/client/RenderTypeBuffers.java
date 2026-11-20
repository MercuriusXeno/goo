package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Where a goo visual writes a render type's vertices, as 26.1's
 * MultiBufferSource handed them out before Minecraft 26.2 removed it.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
@FunctionalInterface
public interface RenderTypeBuffers {

    /**
     * The vertex consumer a render type's vertices go to.
     *
     * @param renderType the render type drawn
     * @return the consumer its vertices go to
     */
    VertexConsumer getBuffer(RenderType renderType);
}
