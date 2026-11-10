package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mercuriusxeno.goo.client.ability.GlowCore;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Glow's Reflector on a prism: the glow column ({@link GlowColumn}) folded into
 * four sides as Relay's is, and glow's beam of light
 * ({@link GlowBeamMesh}) along the rail to each reflector it links to, the
 * beam wider and brighter the more light the network carries. Each beam is
 * drawn once, by the end whose position sorts first.
 * decision reflector-rails-carry-the-brightest-light
 * decision relay-and-metronome-read-apart-at-rest
 * decision reflector-rail-blooms-out-inside-the-prism
 * decision every-glow-beam-breathes-in-brightness
 */
public final class GlowReflectorStyle implements PrismComboStyle {

    /**
     * The id of the ability whose on_prism reaction makes the reflector.
     * channeled-hosts-convert-prisms-over-the-hold
     */
    public static final String COMBO = "goo:glow_sunbeam";
    /** A beam's width at the dimmest light, as a share of its width at the brightest. */
    static final float DIMMEST_WIDTH = 0.3f;
    private static final float BRIGHTEST = 15f;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final double HALF = 0.5;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        GlowColumn.submit(state, poseStack, nodeCollector, restingSides());
        float share = lightShare(state.linkLight);
        float scroll = Mth.frac(-state.animationTime * SCROLL_PER_TICK);
        float breath = GlowBeamMesh.breath(state.animationTime);
        Vec3 center = new Vec3(HALF, HALF, HALF);
        for (Vec3 link : state.links) {
            if (drawsTheBeam(link)) {
                poseStack.pushPose();
                poseStack.translate(center.x, center.y, center.z);
                poseStack.mulPose(GlowBeamMesh.alongBeam(link));
                float length = (float) link.length();
                nodeCollector.order(GlowColumn.BEAM_ORDER).submitCustomGeometry(poseStack,
                        GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION),
                        (pose, buffer) -> emitBeam(pose, buffer, length, share, scroll, breath));
                poseStack.popPose();
            }
        }
        if (!state.links.isEmpty()) {
            submitCore(poseStack, nodeCollector, share);
        }
    }

    /**
     * Draws the soft core of light every rail leaves from, at the prism's
     * middle after its column, scaled and dimmed with the rails.
     * decision diagnose-and-correct-the-sunbeam-prism-depth
     *
     * @param poseStack     the pose at the prism's cell corner
     * @param nodeCollector the submit collector
     * @param share         the network's light as a share of the brightest
     */
    private static void submitCore(PoseStack poseStack, SubmitNodeCollector nodeCollector, float share) {
        float width = DIMMEST_WIDTH + (1f - DIMMEST_WIDTH) * share;
        poseStack.pushPose();
        poseStack.translate(HALF, HALF, HALF);
        nodeCollector.order(GlowColumn.BEAM_ORDER).submitCustomGeometry(poseStack, GooRenderTypes.GLOW_SHELL_TYPE,
                (pose, buffer) -> GlowCore.emit(pose, buffer, width, share));
        poseStack.popPose();
    }

    @Override
    public PrismCrystal.ColumnSides restingSides() {
        return PrismCrystal.ColumnSides.FOUR;
    }

    /**
     * Emits a rail from one prism's middle to the linked prism's middle, each
     * layer fading out past both middles so neither end stops square inside
     * its prism, and every layer breathing in alpha on top of the light share.
     * decision reflector-rail-blooms-out-inside-the-prism
     * decision every-glow-beam-breathes-in-brightness
     *
     * @param pose   the pose at the drawing prism's middle, its +y along the rail
     * @param buffer the vertex consumer
     * @param length how far the linked prism's middle lies, in blocks
     * @param share  the network's light as a share of the brightest
     * @param scroll the texture's scroll offset in [0, 1)
     * @param breath the alpha share {@link GlowBeamMesh#breath} answered, on top of the light share
     */
    static void emitBeam(PoseStack.Pose pose, VertexConsumer buffer, float length, float share, float scroll,
                         float breath) {
        float width = DIMMEST_WIDTH + (1f - DIMMEST_WIDTH) * share;
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            int color = GlowBeamMesh.breathed(layer.color(), share * breath);
            GlowBeamMesh.emitLayerFadingAtBothEnds(pose, buffer, layer.radius() * width, color, scroll, length,
                    layer.depth());
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
