package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import com.mercuriusxeno.goo.ability.program.FieldStrike;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import java.util.List;
import java.util.Optional;

/**
 * Metal spike trap visual: extends goo-textured cone spikes from the
 * chain marker orb out to each struck entity. The spikes are the strikes
 * in flight of the metal_spikes field effect, read from the marker's
 * {@link FieldEffectState}; each animates independently through windup,
 * extension, and retract phases around the tick it lands.
 */
public final class MetalSpikeVisual {

    /** Half-block offset for face and edge positioning. */
    private static final float HALF = 0.5f;

    /** Spike cone base radius in blocks (30% thicker than original). */
    private static final float SPIKE_BASE_RADIUS = 0.104f;
    /** Number of triangular faces on the spike cone. */
    private static final int SPIKE_SIDES = 3;
    /** Alpha for spike cone color. */
    private static final int SPIKE_ALPHA = 0xCC;
    /** Epsilon for near-zero spike length checks. */
    private static final float SPIKE_EPSILON = 1e-4f;
    /** Overshoot past the entity center so the spike pierces through. */
    private static final float SPIKE_OVERSHOOT = 1.0f;
    /** Ticks before the strike lands that the spike starts to emerge. */
    private static final int EMERGE_LEAD = 2;
    /** Ticks the spike holds at full extension after it lands. */
    private static final int HOLD_TICKS = 3;
    /** How far the windup shake swings the orb either side of resting size, at its peak. */
    static final float SHAKE_AMPLITUDE = 0.15f;
    /** Ticks per cycle of the windup shake. */
    static final float SHAKE_PERIOD = 1.5f;
    private static final double TWO_PI = 2 * Math.PI;

    private MetalSpikeVisual() {
    }

    /**
     * Populates {@code state} with the metal trap's spikes in flight, read
     * from the marker's field-effect state, and their timing, read off the
     * field-effect step of the marker's synced ability; a marker of another
     * type draws no spikes.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    public static void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        FieldEffectState field = be.getFieldEffect();
        Optional<FieldEffectStep> step = SyncedSteps.first(be, FieldEffectStep.class);
        boolean metal = GooTypes.METAL.equals(be.getGooType()) && be.getBehavior() != null && step.isPresent();
        Variables variables = Variables.NONE;
        state.spikeAnims = metal ? field.strikes() : List.of();
        state.spikeStrikeTick = step.map(trap -> trap.strikeTick().evaluateInt(variables)).orElse(0);
        state.spikeLength = step.map(trap -> trap.strikeTicks().evaluateInt(variables)).orElse(0);
    }

    /**
     * Computes the spike extension fraction for rendering, phased around
     * the tick the strike lands: no spike through the windup, extending
     * over the {@code EMERGE_LEAD} ticks before landing, holding for
     * {@code HOLD_TICKS} after, then retracting to the end of its length.
     *
     * @param age         the spike's age in ticks
     * @param partialTick the partial tick for smooth interpolation
     * @param strikeTick  the age at which the spike lands
     * @param length      how many ticks the spike stays in flight
     * @return extension fraction in [0, 1]
     */
    static float extensionFraction(int age, float partialTick, int strikeTick, int length) {
        float t = age + partialTick;
        int emerge = strikeTick - EMERGE_LEAD;
        int retract = strikeTick + HOLD_TICKS;
        if (t < emerge) {
            return 0f;
        }
        if (t < strikeTick) {
            return (t - emerge) / EMERGE_LEAD;
        }
        if (t < retract) {
            return 1f;
        }
        if (t < length) {
            return 1f - (t - retract) / (length - retract);
        }
        return 0f;
    }

    /**
     * Computes the orb's shake for one spike (decision
     * orchestration-animation-per-ability): through the windup the orb
     * oscillates rapidly about resting size, building toward the strike,
     * and comes to rest on the tick the spike lands. Outside a windup the
     * orb is still.
     *
     * @param age         the spike's age in ticks
     * @param partialTick the partial tick for smooth interpolation
     * @param strikeTick  the age at which the spike lands
     * @return scale multiplier for the orb, within SHAKE_AMPLITUDE of 1
     */
    static float gooShake(int age, float partialTick, int strikeTick) {
        float t = age + partialTick;
        if (t < 0 || t >= strikeTick) {
            return 1f;
        }
        float buildUp = t / strikeTick;
        double phase = TWO_PI * (strikeTick - t) / SHAKE_PERIOD;
        return 1f + SHAKE_AMPLITUDE * buildUp * (float) Math.sin(phase);
    }

    /**
     * Renders goo-textured cone spikes from the orb center toward each
     * tracked entity, with per-entity animation phasing.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     */
    public static void submit(ChainMarkerRenderState state,
                              PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        int color = ARGB.color(SPIKE_ALPHA, ClientGooTypes.highlight(state.gooType));
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);
        Direction face = state.placedFace;
        float cx = HALF - face.getStepX() * HALF;
        float cy = HALF - face.getStepY() * HALF;
        float cz = HALF - face.getStepZ() * HALF;

        GooSubmitter.submitFluid(poseStack, nodeCollector, ctx -> {
            for (FieldStrike spike : state.spikeAnims) {
                emitSingleSpike(ctx, spike, state, cx, cy, cz, color, uv);
            }
        });
    }

    /**
     * Looks up the fluid sprite and wraps its UV bounds.
     *
     * @param type the goo type to look up
     * @return the UV rectangle for the fluid sprite
     */
    private static GooRenderUtil.UvRect lookupSpriteUv(ResourceKey<GooTypeDefinition> type) {
        return GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(type));
    }

    /**
     * Emits a single spike cone toward a tracked entity position.
     *
     * @param ctx   the render context
     * @param spike the spike in flight, aimed at the point it captured
     * @param state the chain marker render state
     * @param cx    orb center X
     * @param cy    orb center Y
     * @param cz    orb center Z
     * @param color packed ARGB spike color
     * @param uv    fluid sprite UV rectangle
     */
    private static void emitSingleSpike(RenderContext ctx, FieldStrike spike,
                                        ChainMarkerRenderState state, float cx, float cy, float cz,
                                        int color, GooRenderUtil.UvRect uv) {
        float dx = spike.x() - state.blockPos.getX() - cx;
        float dy = spike.y() - state.blockPos.getY() - cy;
        float dz = spike.z() - state.blockPos.getZ() - cz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < SPIKE_EPSILON) {
            return;
        }
        float ext = extensionFraction(spike.age(), state.partialTick, state.spikeStrikeTick, state.spikeLength);
        float tipDist = (len + SPIKE_OVERSHOOT) * ext;
        emitSpikeCone(ctx, cx, cy, cz, dx / len, dy / len, dz / len, tipDist, color, uv);
    }

    /**
     * Emits a 3-sided cone from the base point toward the direction.
     *
     * @param ctx    the render context
     * @param bx     base center X
     * @param by     base center Y
     * @param bz     base center Z
     * @param dirX   normalized direction X
     * @param dirY   normalized direction Y
     * @param dirZ   normalized direction Z
     * @param length the cone length
     * @param color  the ARGB color
     * @param uv     the fluid sprite UV rectangle
     */
    private static void emitSpikeCone(RenderContext ctx,
                                      float bx, float by, float bz,
                                      float dirX, float dirY, float dirZ,
                                      float length, int color, GooRenderUtil.UvRect uv) {
        ConeGeometry.Cone cone = new ConeGeometry.Cone(bx, by, bz, dirX, dirY, dirZ, length, SPIKE_BASE_RADIUS);
        ConeGeometry.emitCone(ctx, cone, ConeGeometry.computeBasis(dirX, dirY, dirZ), SPIKE_SIDES, color, uv);
    }
}
