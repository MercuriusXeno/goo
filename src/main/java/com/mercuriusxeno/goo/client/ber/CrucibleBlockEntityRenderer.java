package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the crucible's liquid level surface.
 */
public class CrucibleBlockEntityRenderer
        implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderState> {

    // -- Color constants --

    /** Maximum alpha channel value (1 byte). */
    private static final int MAX_ALPHA = 255;
    /** Byte mask for clamping to [0, 255]. */
    private static final int BYTE_MASK = 0xFF;

    /** Each crucible's surface agitation, held client-side and dropped with the crucible. */
    private final Map<CrucibleBlockEntity, SurfaceAgitation> agitations = new WeakHashMap<>();

    /**
     * Creates a crucible BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public CrucibleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // No resources needed from context
    }

    @Override
    public CrucibleRenderState createRenderState() {
        return new CrucibleRenderState();
    }

    @Override
    public void extractRenderState(CrucibleBlockEntity be, CrucibleRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        extractPoolState(be, state);
        extractRipple(be, state);
    }

    /**
     * Ticks this crucible's surface agitation on its fill, which moves as an
     * item dissolves into the pool (decision undulating-fluid-surface).
     *
     * @param be the crucible block entity
     * @param state the render state, pool volumes already extracted
     */
    private void extractRipple(CrucibleBlockEntity be, CrucibleRenderState state) {
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        float fill = CrucibleBasin.fillFraction(state.poolVolume + state.reservoirVolume);
        state.rippleAmplitude = agitations.computeIfAbsent(be, key -> new SurfaceAgitation())
            .tick(fill, 0f, gameTick);
    }

    /**
     * Copies pool volumes and crossfade dominant-type fields from the block entity.
     *
     * @param be the crucible block entity
     * @param state the render state to populate
     */
    private static void extractPoolState(CrucibleBlockEntity be, CrucibleRenderState state) {
        state.poolVolume = be.getPoolVolume();
        state.reservoirVolume = be.getReservoir().totalVolume();
        extractCrossfade(be, state);
    }

    /**
     * Ticks the dominant-type fader and copies crossfade fields to the render state.
     * @param be the crucible block entity
     * @param state the render state to populate
     */
    private static void extractCrossfade(CrucibleBlockEntity be, CrucibleRenderState state) {
        if (be.getLevel() != null) {
            be.dominantTypeFader.tick(
                be.getReservoir().largestType(), be.getLevel().getGameTime());
        }
        state.dominantType = be.dominantTypeFader.getShownType();
        state.outgoingType = be.dominantTypeFader.getOutgoingType();
        state.crossfadeAlpha = be.dominantTypeFader.getCrossfadeAlpha();
    }

    @Override
    public void submit(CrucibleRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        submitLiquidLevel(poseStack, nodeCollector, state);
    }

    // -- Liquid level --

    /**
     * Submits the liquid surface quad(s). Renders two during crossfade for smooth blending.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitLiquidLevel(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CrucibleRenderState state) {
        int totalGoo = state.poolVolume + state.reservoirVolume;
        if (totalGoo <= 0 || state.dominantType == null) { return; }

        submitLiquidQuads(poseStack, nodeCollector, state, CrucibleBasin.surfaceYForVolume(totalGoo));
    }

    /**
     * Submits one or two liquid quads depending on whether a crossfade is active.
     * During crossfade, the outgoing type fades out while the incoming type fades in.
     * The submitter lights the surface fullbright; the basin model uses world light.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the crucible render state (dominantType must be non-null)
     * @param surfaceY the computed liquid surface Y height
     */
    private static void submitLiquidQuads(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CrucibleRenderState state,
            float surfaceY) {
        if (state.outgoingType != null) {
            submitCrossfadeQuads(poseStack, nodeCollector, state, surfaceY);
        } else {
            submitLiquidQuad(poseStack, nodeCollector, state.dominantType, surfaceY, 1f,
                state.rippleAmplitude);
        }
    }

    /**
     * Submits two overlapping liquid quads for a crossfade transition:
     * the outgoing type fading out and the incoming type fading in.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the crucible render state with crossfade fields
     * @param surfaceY the computed liquid surface Y height
     */
    private static void submitCrossfadeQuads(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CrucibleRenderState state,
            float surfaceY) {
        float outAlpha = 1f - state.crossfadeAlpha;
        submitLiquidQuad(poseStack, nodeCollector, state.outgoingType, surfaceY, outAlpha,
            state.rippleAmplitude);
        submitLiquidQuad(poseStack, nodeCollector, state.dominantType, surfaceY,
            state.crossfadeAlpha, state.rippleAmplitude);
    }

    /**
     * Submits a single liquid surface quad for one goo type through the
     * submitter's color-taking fluid submission, so the crossfade alpha
     * reaches the vertex color.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param type the goo type
     * @param surfaceY the surface Y height
     * @param alpha the alpha transparency [0, 1]
     * @param amplitude the ripple amplitude the tracker answered
     */
    private static void submitLiquidQuad(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, ResourceKey<GooTypeDefinition> type,
            float surfaceY, float alpha, float amplitude) {
        TextureAtlasSprite sprite = GooSubmitter.fluidSprite(type);
        GooSubmitter.submitUndulatingFluid(poseStack, nodeCollector, packArgb(alpha, GooSubmitter.fluidTint(type)),
            ctx -> emitLiquidSurface(ctx, surfaceY, sprite, amplitude));
    }

    /**
     * Packs an alpha fraction [0, 1] onto the RGB channels of a tint.
     *
     * @param alpha the alpha transparency [0, 1]
     * @param tint  the ARGB tint whose RGB channels are kept
     * @return the packed ARGB color
     */
    static int packArgb(float alpha, int tint) {
        int a = (int) (alpha * MAX_ALPHA) & BYTE_MASK;
        return ARGB.color(a, tint);
    }

    /**
     * Emits the liquid surface grid over the basin interior bounds, at the
     * context's light and color, its rim held still inside the basin walls.
     *
     * @param ctx the render context the submitter built
     * @param surfaceY the liquid surface Y height
     * @param sprite the fluid texture atlas sprite
     * @param amplitude the ripple amplitude the interior vertices carry
     */
    private static void emitLiquidSurface(RenderContext ctx, float surfaceY,
            TextureAtlasSprite sprite, float amplitude) {
        emitLiquidSurface(ctx, surfaceY, new GooRenderUtil.UvRect(
            sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()), amplitude);
    }

    /**
     * Emits the liquid surface grid over the basin interior bounds on a UV rect.
     *
     * @param ctx the render context the submitter built
     * @param surfaceY the liquid surface Y height
     * @param uv the sprite's UV rect
     * @param amplitude the ripple amplitude the interior vertices carry
     */
    static void emitLiquidSurface(RenderContext ctx, float surfaceY, GooRenderUtil.UvRect uv,
            float amplitude) {
        CuboidBounds basin = new CuboidBounds(CrucibleBasin.FOOTPRINT_MIN, CrucibleBasin.FOOTPRINT_MAX,
            CrucibleBasin.FOOTPRINT_MIN, CrucibleBasin.FOOTPRINT_MAX, CrucibleBasin.FLOOR_Y, surfaceY);
        ctx.liquidSurfaceGrid(basin, uv, amplitude);
    }

}
