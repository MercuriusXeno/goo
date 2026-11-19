package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * One batch a draw fills and then submits: the consumer its vertices go to
 * and the act that ends the batch, so a test hands a draw a counting
 * consumer and counts what one frame submits headless.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 *
 * @param consumer where the batch's vertices go
 * @param end      ends the batch, submitting it
 */
public record BufferedPass(VertexConsumer consumer, Runnable end) {

    /**
     * The batch a render type fills in the frame's buffers.
     *
     * @param buffers the frame's buffers
     * @param type    the render type the batch draws through
     * @return the batch
     */
    public static BufferedPass of(MultiBufferSource.BufferSource buffers, RenderType type) {
        return new BufferedPass(buffers.getBuffer(type), () -> buffers.endBatch(type));
    }
}
