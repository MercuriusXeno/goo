package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import java.util.List;

/**
 * Bulb's beacon on a prism: the plain column with a glow beam rising out of
 * it along its facing, as far as a vanilla beacon's beam reaches, so it reads
 * from far away. The beam is layers blended additively: a thin near-white core
 * inside wider, fainter glow-yellow shells, so it blooms and stays see-through.
 * decision bulb-one-model-max-light-beacon-combo
 */
public final class GlowBeaconStyle implements PrismComboStyle {

    /** The id of the ability whose on_prism reaction is the beacon: Bulb. */
    public static final String COMBO = "goo:glow_crystal";
    /** How far the beam reaches out of the prism, in blocks: a vanilla beacon's reach. */
    static final int BEAM_REACH = BeaconRenderer.MAX_RENDER_Y;
    /** The beam's layers from the core out, each wider and fainter than the last. */
    static final List<Layer> LAYERS = List.of(
            new Layer(0.06f, ARGB.color(200, 0xFFF6C8)),
            new Layer(0.13f, ARGB.color(110, 0xFFE628)),
            new Layer(0.24f, ARGB.color(45, 0xFFD700)),
            new Layer(0.40f, ARGB.color(18, 0xFFD700)));
    private static final float HALF = 0.5f;
    private static final float SPIN_DEGREES_PER_TICK = 2.25f;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final float TEXTURE_BLOCKS_PER_TILE = 1f;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look != null) {
            poseStack.pushPose();
            PrismCrystal.standOnLandingFace(poseStack, state.facing);
            CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, look, state.lightCoords);
            poseStack.popPose();
        }
        poseStack.pushPose();
        poseStack.translate(HALF, HALF, HALF);
        poseStack.mulPose(state.facing.getRotation());
        poseStack.translate(0f, -HALF, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.animationTime * SPIN_DEGREES_PER_TICK));
        float scroll = Mth.frac(-state.animationTime * SCROLL_PER_TICK);
        for (Layer layer : LAYERS) {
            float radius = layer.radius() * state.beamRadiusScale;
            nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION),
                    (pose, buffer) -> emitLayer(pose, buffer, radius, layer.color(), scroll));
        }
        poseStack.popPose();
    }

    @Override
    public int beamReach() {
        return BEAM_REACH;
    }

    /**
     * Emits one layer: a square tube of four faces around the beam's axis,
     * from the prism's base out to the beam's reach.
     *
     * @param pose   the pose, its +y along the beam
     * @param buffer the vertex consumer
     * @param radius the tube's half-width in blocks
     * @param color  the layer's packed ARGB color
     * @param scroll the texture's scroll offset in [0, 1)
     */
    static void emitLayer(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color, float scroll) {
        float vTop = scroll + BEAM_REACH / TEXTURE_BLOCKS_PER_TILE;
        float[][] corners = {{-radius, -radius}, {radius, -radius}, {radius, radius}, {-radius, radius}};
        for (int side = 0; side < corners.length; side++) {
            float[] from = corners[side];
            float[] to = corners[(side + 1) % corners.length];
            vertex(pose, buffer, color, from[0], 0, from[1], 0f, scroll);
            vertex(pose, buffer, color, to[0], 0, to[1], 1f, scroll);
            vertex(pose, buffer, color, to[0], BEAM_REACH, to[1], 1f, vTop);
            vertex(pose, buffer, color, from[0], BEAM_REACH, from[1], 0f, vTop);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z,
                               float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(GooSubmitter.fullbrightLight()).setNormal(pose, 0f, 1f, 0f);
    }

    /**
     * One layer of the beam.
     *
     * @param radius the layer's half-width in blocks before the distance widening
     * @param color  the layer's packed ARGB color, its alpha how strongly it adds
     */
    record Layer(float radius, int color) {
    }
}
