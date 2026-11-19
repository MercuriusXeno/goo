package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The collector one ghost of a blink's trail submits through: the body
 * model draws at once through the ghost render type under the ghost's goo
 * tint and fade, posed as the renderer posed it, as vanilla's model feature
 * draws a submitted model; the rest of the entity is dropped.
 * Decision ghost-trail-spans-the-blink.
 */
final class GhostCollector extends BodyOnlyCollector {

    private final MultiBufferSource buffers;
    private final RenderType ghostType;
    private final int ghostColor;

    /**
     * @param buffers    the buffers the ghost draws into
     * @param body       the entity renderer's body model
     * @param ghostType  the ghost render type over the body's skin
     * @param ghostColor the ghost's ARGB color, its fade in the alpha
     */
    GhostCollector(MultiBufferSource buffers, Model<?> body, RenderType ghostType, int ghostColor) {
        super(body);
        this.buffers = buffers;
        this.ghostType = ghostType;
        this.ghostColor = ghostColor;
    }

    @Override
    <S> void onBody(Model<? super S> model, S state, PoseStack poseStack, int lightCoords) {
        model.setupAnim(state);
        model.renderToBuffer(poseStack, buffers.getBuffer(ghostType), lightCoords, OverlayTexture.NO_OVERLAY,
                ghostColor);
    }
}
