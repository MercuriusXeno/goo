package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Glow goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): an aurora bloom over the crystal the
 * marker just placed, drawn from the burnout alone since the marker is gone
 * the tick it fires. A soft dome of light rises out of the placed face over
 * 20 ticks on an ease-out, to about 1.25 blocks over the large crystal and
 * less over each smaller one, in step with the crystal's lateral extent
 * (decision glow-dome-scales-with-the-crystal). Its fragment shader
 * ({@code glow_explosion.fsh}) draws vertical aurora bands, FFFF28 at the
 * base shading to FFD700 and a pale white crown, sliding slowly around the
 * dome like the fade walls' curtains, and discards the half of the sphere
 * behind the face. Additive, breathing brighter once then fading as the
 * crystal takes over the light. The vertex color carries progress in red,
 * the placed face's ordinal in green and the bloom's brightness in blue,
 * since a core pipeline takes no per-draw uniforms.
 */
public final class GlowExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final GlowExplosionVisual INSTANCE = new GlowExplosionVisual();

    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = 20;
    /** The dome's full radius in blocks over the large crystal, four stacks. */
    static final float LARGE_DOME_REACH = 1.25f;
    /** The share of the explosion at which the bloom breathes brightest. */
    static final float BREATH_PEAK = 0.3f;
    /** How far the dome's center sits from the block center along the face's step: on the face plane. */
    private static final float DOME_LIFT = -0.5f;
    private static final int OPAQUE = 0xFF;
    /** The progress the burnout's first drawn frame shows, which the fuse-tail ramp ends on. */
    static final float FIRST_DRAWN_PROGRESS = DomeRamp.firstDrawnProgress(DURATION_TICKS);

    private GlowExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.GLOW;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float radius = domeRadius(progress, burnout.stackCount());
        int color = domeColor(progress, burnout.placedFace(), OPAQUE);
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.GLOW_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, burnout.placedFace(), DOME_LIFT, radius, color));
    }

    @Override
    public void submitRamp(ChainBurnouts.Burnout burnout, float ramp, PoseStack poseStack,
                           SubmitNodeCollector collector) {
        float radius = rampRadius(ramp, burnout.stackCount());
        int color = domeColor(FIRST_DRAWN_PROGRESS, burnout.placedFace(), DomeRamp.alpha(ramp));
        collector.submitCustomGeometry(poseStack, GooRenderTypes.GLOW_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, burnout.placedFace(), DOME_LIFT, radius, color));
    }

    /**
     * The dome's radius through the fuse-tail ramp, meeting the burnout's
     * first drawn frame at the same stack count.
     * Decision dome-fades-in-before-its-start.
     * Decision glow-dome-scales-with-the-crystal.
     *
     * @param ramp   the ramp's share in [0, 1]
     * @param stacks the marker's stack count
     * @return the dome's radius in blocks
     */
    static float rampRadius(float ramp, int stacks) {
        return DomeRamp.radius(ramp, domeRadius(FIRST_DRAWN_PROGRESS, stacks));
    }

    /**
     * The dome's full reach for a stack count: the large crystal's reach
     * scaled by that crystal size's lateral extent over the large one's.
     * Decision glow-dome-scales-with-the-crystal.
     *
     * @param stacks the marker's stack count
     * @return the dome's full reach in blocks
     */
    static float domeReach(int stacks) {
        return LARGE_DOME_REACH * lateralExtent(GlowCrystalBlock.CrystalSize.fromStacks(stacks))
                / lateralExtent(GlowCrystalBlock.CrystalSize.LARGE);
    }

    private static float lateralExtent(GlowCrystalBlock.CrystalSize size) {
        return (float) (size.max - size.min);
    }

    /**
     * Packs the dome's vertex color: opacity in alpha, which the shader
     * multiplies into its output alpha, progress in red, the placed face's
     * ordinal in green and the bloom's brightness in blue.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param face     the placed face
     * @param alpha    the dome's opacity as a byte
     * @return the packed ARGB color
     */
    static int domeColor(float progress, Direction face, int alpha) {
        return ARGB.color(alpha, NetherDiscMesh.toByte(progress), face.ordinal(),
                NetherDiscMesh.toByte(brightness(progress)));
    }

    /**
     * The aurora dome's radius: an ease-out growth to its stack count's reach.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param stacks   the marker's stack count
     * @return the dome's radius in blocks
     */
    static float domeRadius(float progress, int stacks) {
        return domeReach(stacks) * BurnoutGeometry.easeOutCubic(progress);
    }

    /**
     * The bloom's brightness: breathing up to full at BREATH_PEAK, then
     * fading to nothing as the crystal takes over the light.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the brightness in [0, 1]
     */
    static float brightness(float progress) {
        if (progress < BREATH_PEAK) {
            return BurnoutGeometry.easeOutCubic(progress / BREATH_PEAK);
        }
        return BurnoutGeometry.fadeAfter(progress, BREATH_PEAK);
    }
}
