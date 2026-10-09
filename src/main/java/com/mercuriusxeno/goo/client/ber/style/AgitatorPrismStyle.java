package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Hex's agitator prism: the plain crystal, swelling faintly and glowing
 * with each spawn attempt, the beat fading between attempts, so the pulse
 * quickens as failed attempts shorten the interval and slows again once a
 * monster spawns.
 * agitator-prism-quickens-until-a-spawn
 */
public final class AgitatorPrismStyle implements PrismComboStyle {

    /** The combo this style draws: the id of hex's agitator ability. */
    public static final String COMBO = "goo:hex_agitator";

    /** How far the crystal swells at a beat's peak. */
    private static final float SWELL = 0.06f;
    /** The ticks a beat takes to fade to a third. */
    private static final float BEAT_FADE_TICKS = 5f;
    private static final float HALF = 0.5f;
    private static final int NO_OUTLINE = 0;

    /**
     * How strongly the beat shows some ticks after the last attempt: whole
     * at the attempt, fading quickly after.
     *
     * @param ticksSinceAttempt ticks since the last attempt, the partial tick among them
     * @return 0 to 1
     */
    public static float beat(float ticksSinceAttempt) {
        return (float) Math.exp(-Math.max(0f, ticksSinceAttempt) / BEAT_FADE_TICKS);
    }

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        float swell = 1f + SWELL * state.beat;
        poseStack.pushPose();
        poseStack.translate(HALF, HALF, HALF);
        poseStack.scale(swell, swell, swell);
        poseStack.translate(-HALF, -HALF, -HALF);
        int light = state.beat > HALF ? GooSubmitter.fullbrightLight() : state.lightCoords;
        state.crystal.submit(poseStack, nodeCollector, light, OverlayTexture.NO_OVERLAY, NO_OUTLINE);
        poseStack.popPose();
    }
}
