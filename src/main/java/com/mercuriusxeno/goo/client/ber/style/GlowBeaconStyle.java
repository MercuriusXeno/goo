package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;

/**
 * Bulb's beacon on a prism: the glow column ({@link GlowColumn}) with glow's beam of light
 * ({@link GlowBeamMesh}) rising out of it along its facing, as far as a
 * vanilla beacon's beam reaches, so it reads from far away.
 * decision bulb-one-model-max-light-beacon-combo
 */
public final class GlowBeaconStyle implements PrismComboStyle {

    /** The id of the ability whose on_prism reaction is the beacon: Bulb. */
    public static final String COMBO = "goo:glow_crystal";
    /** How far the beam reaches out of the prism, in blocks: a vanilla beacon's reach. */
    static final int BEAM_REACH = BeaconRenderer.MAX_RENDER_Y;
    private static final float HALF = 0.5f;
    private static final float SPIN_DEGREES_PER_TICK = 2.25f;
    private static final float SCROLL_PER_TICK = 0.2f;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        GlowColumn.submit(state, poseStack, nodeCollector);
        poseStack.pushPose();
        poseStack.translate(HALF, HALF, HALF);
        poseStack.mulPose(state.facing.getRotation());
        poseStack.translate(0f, -HALF, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.animationTime * SPIN_DEGREES_PER_TICK));
        float scroll = Mth.frac(-state.animationTime * SCROLL_PER_TICK);
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            float radius = layer.radius() * state.beamRadiusScale;
            nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION),
                    (pose, buffer) -> GlowBeamMesh.emitLayer(pose, buffer, radius, layer.color(), scroll, BEAM_REACH));
        }
        poseStack.popPose();
    }

    @Override
    public int beamReach() {
        return BEAM_REACH;
    }
}
