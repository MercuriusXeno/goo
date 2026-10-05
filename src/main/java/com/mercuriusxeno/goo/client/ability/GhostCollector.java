package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The collector one ghost of a blink's trail submits through: the body
 * model passes on to the frame through the ghost render type under the
 * ghost's goo tint and fade; the rest of the entity is dropped.
 * Decision ghost-trail-spans-the-blink.
 */
final class GhostCollector extends BodyOnlyCollector {

    /** No outline on a ghost. */
    private static final int NO_OUTLINE = 0;

    private final SubmitNodeCollector frame;
    private final RenderType ghostType;
    private final int ghostColor;

    /**
     * @param frame      the frame's own collector the ghost draws through
     * @param body       the entity renderer's body model
     * @param ghostType  the ghost render type over the body's skin
     * @param ghostColor the ghost's ARGB color, its fade in the alpha
     */
    GhostCollector(SubmitNodeCollector frame, Model<?> body, RenderType ghostType, int ghostColor) {
        super(body);
        this.frame = frame;
        this.ghostType = ghostType;
        this.ghostColor = ghostColor;
    }

    @Override
    <S> void onBody(Model<? super S> model, S state, PoseStack poseStack, int lightCoords) {
        frame.submitModel(model, state, poseStack, ghostType, lightCoords, OverlayTexture.NO_OVERLAY, ghostColor, null,
                NO_OUTLINE, null);
    }
}
