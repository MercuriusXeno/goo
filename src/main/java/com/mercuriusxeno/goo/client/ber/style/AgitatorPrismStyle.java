package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Hex's agitator prism: the plain quartz column, swelling faintly about its
 * base and glowing with each spawn attempt, the beat fading between attempts, so the pulse
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
    private static final float PIXELS_PER_BLOCK = 16f;
    /** The column's base point in block units, the point it swells about. */
    private static final float BASE_X = (float) CrystalCluster.BASE_X / PIXELS_PER_BLOCK;
    private static final float BASE_Y = (float) CrystalCluster.BASE_Y / PIXELS_PER_BLOCK;
    private static final float BASE_Z = (float) CrystalCluster.BASE_Z / PIXELS_PER_BLOCK;

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
        CrystalClusterSubmitter.Look look = state.look;
        if (look == null) {
            return;
        }
        float swell = 1f + SWELL * state.beat;
        poseStack.pushPose();
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        poseStack.translate(BASE_X, BASE_Y, BASE_Z);
        poseStack.scale(swell, swell, swell);
        poseStack.translate(-BASE_X, -BASE_Y, -BASE_Z);
        int light = state.beat > HALF ? GooSubmitter.fullbrightLight() : state.lightCoords;
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, look, light);
        poseStack.popPose();
    }
}
