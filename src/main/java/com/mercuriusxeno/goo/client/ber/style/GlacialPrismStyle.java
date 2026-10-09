package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.FrostExplosionVisual;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * A glacial prism's look: the crystal as it stands, with frost's fog lying
 * whole about its base, a cold collar that marks the prism as the one
 * holding its ground frozen (decision glacial-prism-holds-the-area-frozen).
 */
public final class GlacialPrismStyle implements PrismComboStyle {

    /** The combo id a glacial prism carries, frost's prism ability. */
    public static final String COMBO = "goo:frost_glacial";

    /** The collar's reach about the crystal's base, in blocks. */
    private static final float COLLAR_REACH = 0.9f;
    /** How far the collar sits from the block center toward the face the crystal grew from. */
    private static final float COLLAR_LIFT = -0.45f;
    private static final int NO_OUTLINE = 0;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        state.crystal.submit(poseStack, nodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, NO_OUTLINE);
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.FROST_EXPLOSION_TYPE, (pose, consumer) ->
                FrostExplosionVisual.emitWholeFog(pose, consumer, state.facing, COLLAR_LIFT, COLLAR_REACH, 1f));
    }
}
