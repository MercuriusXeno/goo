package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.GlowQueue;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;

/**
 * Bulb's beacon on a prism: the glow column ({@link GlowColumn}) with glow's beam of light
 * ({@link GlowBeamMesh}) rising out of it along its facing, as far as a
 * vanilla beacon's beam reaches, so it reads from far away. The beam writes
 * no depth, so the renderer queues it for the shared path, which draws it
 * after the clouds (operator ruling 2026-11-18).
 * decision bulb-one-model-max-light-beacon-combo
 * decision every-glow-beam-breathes-in-brightness
 */
public final class GlowBeaconStyle implements PrismComboStyle {

    /** The id of the ability whose on_prism reaction is the beacon: Bulb. */
    public static final String COMBO = "goo:glow_bulb";
    /** How far the beam reaches out of the prism, in blocks: a vanilla beacon's reach. */
    static final int BEAM_REACH = BeaconRenderer.MAX_RENDER_Y;
    private static final float HALF = 0.5f;
    private static final float SPIN_DEGREES_PER_TICK = 2.25f;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final GlowQueue QUEUED = new GlowQueue();

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        GlowColumn.queue(state, poseStack, restingSides());
        // Sorted at the prism's center, as its column is, so each beam draws after the column it rises from.
        Vec3 center = Vec3.atCenterOf(state.blockPos);
        poseStack.pushPose();
        poseStack.translate(HALF, HALF, HALF);
        poseStack.rotate(state.facing.getRotation());
        poseStack.translate(0f, -HALF, 0f);
        poseStack.rotate(Axis.YP.rotationDegrees(state.animationTime * SPIN_DEGREES_PER_TICK));
        float scroll = Mth.frac(-state.animationTime * SCROLL_PER_TICK);
        float breath = GlowBeamMesh.breath(state.animationTime);
        float radiusScale = state.beamRadiusScale;
        // Every layer in one batch, as the layers drew in the main pass.
        QUEUED.queue(poseStack.last().copy(), center, GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION),
                (pose, buffer) -> {
                    for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
                        emitBeaconLayer(pose, buffer, layer, radiusScale, scroll, breath);
                    }
                });
        poseStack.popPose();
    }

    /**
     * Hands the shared path one draw for each beam the beacons queued this
     * frame, each sorted by the camera's distance to its prism's center, then
     * empties the queue.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision bulb-one-model-max-light-beacon-combo
     *
     * @param frame the frame being drawn
     * @param draws where each beam's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        QUEUED.drain(frame, draws);
    }

    /**
     * Emits one layer of the beacon's beam, its alpha and its width both
     * scaled by the breath, so the whole beam dims and slims slightly at the
     * ease and never grows past its own width (operator UAT 2026-11-10).
     * decision every-glow-beam-breathes-in-brightness
     *
     * @param pose        the pose at the beam's foot, its +y along the beam
     * @param buffer      the vertex consumer
     * @param layer       the layer
     * @param radiusScale the factor the layer's radius takes
     * @param scroll      the texture's scroll offset in [0, 1)
     * @param breath      the share of alpha and width {@link GlowBeamMesh#breath} answered
     */
    static void emitBeaconLayer(PoseStack.Pose pose, VertexConsumer buffer, GlowBeamMesh.Layer layer,
                                float radiusScale, float scroll, float breath) {
        GlowBeamMesh.emitLayer(pose, buffer, layer.radius() * radiusScale * breath,
                GlowBeamMesh.breathed(layer.color(), breath), scroll, BEAM_REACH);
    }

    @Override
    public int beamReach() {
        return BEAM_REACH;
    }
}
