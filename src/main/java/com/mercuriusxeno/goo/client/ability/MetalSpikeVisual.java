package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.FieldStrike;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import java.util.List;

/**
 * Metal spike trap visual: extends goo-textured cone spikes from the
 * chain marker orb out to each struck entity. The spikes are the strikes
 * in flight of the metal_spikes field effect, read from the marker's
 * {@link FieldEffectState}; each animates independently through windup,
 * extension, and retract phases around the tick it lands.
 */
public final class MetalSpikeVisual {

    /** Block atlas path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Half-block offset for face and edge positioning. */
    private static final float HALF = 0.5f;

    /** Spike cone base radius in blocks (30% thicker than original). */
    private static final float SPIKE_BASE_RADIUS = 0.104f;
    /** Number of triangular faces on the spike cone. */
    private static final int SPIKE_SIDES = 3;
    /** Two pi for angle computation. */
    private static final float TWO_PI = (float) (2 * Math.PI);
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
    /** Blob contraction scale during windup (0 = no change, positive = smaller). */
    private static final float WINDUP_CONTRACT = 0.30f;

    private MetalSpikeVisual() {
    }

    /**
     * Populates {@code state} with the metal trap's spikes in flight, read
     * from the marker's field-effect state; a marker of another type draws
     * no spikes.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    public static void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        FieldEffectState field = be.getFieldEffect();
        boolean metal = GooTypes.METAL.equals(be.getGooType()) && be.getBehavior() != null;
        state.spikeAnims = metal ? field.strikes() : List.of();
        state.spikeStrikeTick = field.strikeTick();
        state.spikeLength = field.strikeTicks();
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
     * Computes the blob contraction scale for one spike: the orb squeezes
     * through the windup and returns to size as the spike emerges.
     *
     * @param age         the spike's age in ticks
     * @param partialTick the partial tick for smooth interpolation
     * @param strikeTick  the age at which the spike lands
     * @return scale multiplier for the orb, in [1 - WINDUP_CONTRACT, 1]
     */
    static float blobContraction(int age, float partialTick, int strikeTick) {
        float t = age + partialTick;
        int emerge = strikeTick - EMERGE_LEAD;
        if (t < 0) {
            return 1f;
        }
        if (t < emerge) {
            float contractCurve = (float) Math.sin(t / emerge * Math.PI);
            return 1f - WINDUP_CONTRACT * contractCurve;
        }
        if (t < strikeTick) {
            return 1f - WINDUP_CONTRACT * (1f - (t - emerge) / EMERGE_LEAD);
        }
        return 1f;
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

        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, consumer) -> {
                    RenderContext ctx = new RenderContext(pose, consumer,
                            LightCoordsUtil.FULL_BRIGHT);
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
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        return new GooRenderUtil.UvRect(
                sprite.getU(0f), sprite.getV(0f),
                sprite.getU(1f), sprite.getV(1f));
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
        float tipX = bx + dirX * length;
        float tipY = by + dirY * length;
        float tipZ = bz + dirZ * length;

        float[] basis = ConeGeometry.computeBasis(dirX, dirY, dirZ);
        emitConeFaces(ctx, bx, by, bz, tipX, tipY, tipZ, dirX, dirY, dirZ,
                basis, color, uv);
    }

    /**
     * Emits textured triangular fan faces around the cone from base to tip.
     *
     * @param ctx   the render context
     * @param bx    cone base X
     * @param by    cone base Y
     * @param bz    cone base Z
     * @param tipX  cone tip X
     * @param tipY  cone tip Y
     * @param tipZ  cone tip Z
     * @param dirX  cone direction X
     * @param dirY  cone direction Y
     * @param dirZ  cone direction Z
     * @param basis orthonormal basis from {@link #computeConeBasis}
     * @param color packed ARGB color
     * @param uv    goo fluid sprite UV rectangle
     */
    private static void emitConeFaces(RenderContext ctx,
                                      float bx, float by, float bz,
                                      float tipX, float tipY, float tipZ,
                                      float dirX, float dirY, float dirZ,
                                      float[] basis, int color, GooRenderUtil.UvRect uv) {
        float uMid = (uv.u0() + uv.u1()) * HALF;
        for (int i = 0; i < SPIKE_SIDES; i++) {
            emitConeSegment(ctx, basis, color, uv, uMid,
                    bx, by, bz, tipX, tipY, tipZ, dirX, dirY, dirZ, i);
        }
    }

    /**
     * Emits one triangular segment of a spike cone.
     *
     * @param ctx   the render context
     * @param basis orthonormal basis vectors
     * @param color packed ARGB cone color
     * @param uv    fluid sprite UV rectangle
     * @param uMid  U-axis midpoint for the tip vertex
     * @param bx    cone base center X
     * @param by    cone base center Y
     * @param bz    cone base center Z
     * @param tipX  cone tip X
     * @param tipY  cone tip Y
     * @param tipZ  cone tip Z
     * @param dirX  cone direction X for tip normal
     * @param dirY  cone direction Y for tip normal
     * @param dirZ  cone direction Z for tip normal
     * @param i     segment index around the cone
     */
    private static void emitConeSegment(RenderContext ctx, float[] basis,
                                        int color, GooRenderUtil.UvRect uv, float uMid,
                                        float bx, float by, float bz, float tipX, float tipY, float tipZ,
                                        float dirX, float dirY, float dirZ, int i) {
        float a0 = TWO_PI * i / SPIKE_SIDES;
        float a1 = TWO_PI * (i + 1) / SPIKE_SIDES;
        float cos0 = (float) Math.cos(a0) * SPIKE_BASE_RADIUS;
        float sin0 = (float) Math.sin(a0) * SPIKE_BASE_RADIUS;
        float cos1 = (float) Math.cos(a1) * SPIKE_BASE_RADIUS;
        float sin1 = (float) Math.sin(a1) * SPIKE_BASE_RADIUS;
        float midCos = (float) Math.cos(a0 + a1) * HALF;
        float midSin = (float) Math.sin(a0 + a1) * HALF;
        float nx = basis[ConeGeometry.PERP_X] * midCos + basis[ConeGeometry.CROSS_X] * midSin;
        float ny = basis[ConeGeometry.PERP_Y] * midCos + basis[ConeGeometry.CROSS_Y] * midSin;
        float nz = basis[ConeGeometry.PERP_Z] * midCos + basis[ConeGeometry.CROSS_Z] * midSin;
        ctx.vertexColored(color,
                bx + basis[ConeGeometry.PERP_X] * cos0 + basis[ConeGeometry.CROSS_X] * sin0,
                by + basis[ConeGeometry.PERP_Y] * cos0 + basis[ConeGeometry.CROSS_Y] * sin0,
                bz + basis[ConeGeometry.PERP_Z] * cos0 + basis[ConeGeometry.CROSS_Z] * sin0,
                uv.u0(), uv.v0(), nx, ny, nz);
        ctx.vertexColored(color,
                bx + basis[ConeGeometry.PERP_X] * cos1 + basis[ConeGeometry.CROSS_X] * sin1,
                by + basis[ConeGeometry.PERP_Y] * cos1 + basis[ConeGeometry.CROSS_Y] * sin1,
                bz + basis[ConeGeometry.PERP_Z] * cos1 + basis[ConeGeometry.CROSS_Z] * sin1,
                uv.u1(), uv.v0(), nx, ny, nz);
        ctx.vertexColored(color, tipX, tipY, tipZ, uMid, uv.v1(), dirX, dirY, dirZ);
        ctx.vertexColored(color, tipX, tipY, tipZ, uMid, uv.v1(), dirX, dirY, dirZ);
    }
}
