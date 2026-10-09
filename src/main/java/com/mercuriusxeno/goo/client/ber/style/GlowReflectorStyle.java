package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Glow's Reflector on a prism: the glow column ({@link GlowColumn}), and glow's beam of light
 * ({@link GlowBeamMesh}) along the rail to each reflector it links to, the
 * beam wider and brighter the more light the network carries. Each beam is
 * drawn once, by the end whose position sorts first.
 * decision reflector-rails-carry-the-brightest-light
 */
public final class GlowReflectorStyle implements PrismComboStyle {

    /** The id of the ability whose program makes the reflector. */
    public static final String COMBO = "goo:glow_reflector";
    /** A beam's width at the dimmest light, as a share of its width at the brightest. */
    static final float DIMMEST_WIDTH = 0.3f;
    private static final float BRIGHTEST = 15f;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final double HALF = 0.5;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        GlowColumn.submit(state, poseStack, nodeCollector);
        float share = lightShare(state.linkLight);
        float scroll = Mth.frac(-state.animationTime * SCROLL_PER_TICK);
        Vec3 center = new Vec3(HALF, HALF, HALF);
        for (Vec3 link : state.links) {
            if (drawsTheBeam(link)) {
                poseStack.pushPose();
                poseStack.translate(center.x, center.y, center.z);
                poseStack.mulPose(GlowBeamMesh.alongBeam(link));
                float length = (float) link.length();
                nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION),
                        (pose, buffer) -> emitBeam(pose, buffer, length, share, scroll));
                poseStack.popPose();
            }
        }
    }

    private static void emitBeam(PoseStack.Pose pose, VertexConsumer buffer, float length, float share, float scroll) {
        float width = DIMMEST_WIDTH + (1f - DIMMEST_WIDTH) * share;
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            int color = ARGB.color(Math.round(ARGB.alpha(layer.color()) * share), layer.color());
            GlowBeamMesh.emitLayer(pose, buffer, layer.radius() * width, color, scroll, length);
        }
    }

    /**
     * How bright a beam draws at the network's light, as a share of the brightest.
     *
     * @param light the network's light, 0 to 15
     * @return the share, zero to one
     */
    static float lightShare(int light) {
        return Mth.clamp(light / BRIGHTEST, 0f, 1f);
    }

    /**
     * Whether this end draws the beam to a link: the end the link lies ahead
     * of, so each beam draws once.
     *
     * @param link the linked reflector's offset from this one
     * @return true for the end that draws it
     */
    static boolean drawsTheBeam(Vec3 link) {
        if (link.x != 0) {
            return link.x > 0;
        }
        if (link.y != 0) {
            return link.y > 0;
        }
        return link.z > 0;
    }
}
