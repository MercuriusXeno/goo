package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.client.BandedSurfaceSubmitter;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the crucible's liquid level surface.
 */
public class CrucibleBlockEntityRenderer
        implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderState> {

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
        float fill = CrucibleBasin.heightFraction(state.surfaceVolume);
        state.rippleAmplitude = agitations.computeIfAbsent(be, key -> new SurfaceAgitation())
            .tick(fill, 0f, gameTick);
    }

    /**
     * Copies pool volumes and the type bands the surface draws from the block entity.
     *
     * @param be the crucible block entity
     * @param state the render state to populate
     */
    private static void extractPoolState(CrucibleBlockEntity be, CrucibleRenderState state) {
        state.surfaceVolume = be.getSurfaceVolume();
        state.typeBands = TypeBands.over(surfaceContents(be));
    }

    /**
     * Answers the goo the surface shows: the reservoir, or the melting item's
     * goo while the reservoir holds none, as the bubble color reads it.
     *
     * @param be the crucible block entity
     * @return the contents the type bands partition
     */
    private static GooContents surfaceContents(CrucibleBlockEntity be) {
        GooContents reservoir = be.getReservoir();
        if (!reservoir.isEmpty() || be.getMeltingItem().isEmpty()) {
            return reservoir;
        }
        return PartiallyMeltedItem.getContents(be.getMeltingItem());
    }

    @Override
    public void submit(CrucibleRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        long melted = state.surfaceVolume;
        if (melted <= 0) { return; }
        renderMingledSurface(GooSubmitter.bandedSurfaces(poseStack, nodeCollector), state,
            CrucibleBasin.footprintForVolume(melted), CrucibleBasin.surfaceYForVolume(melted));
    }

    // -- Liquid level --

    /**
     * Submits the liquid surface once per type layer, each lifted above the
     * one below, so the types mingle by noise in place of a dominant-type
     * crossfade (decision mingling-on-vat-and-crucible). The submitter
     * lights the surface fullbright; the basin model uses world light.
     *
     * @param submitter submits one band's surface on that band type's sprite
     * @param state the crucible render state
     * @param footprint the square the goo covers
     * @param surfaceY the computed liquid surface Y height
     */
    static void renderMingledSurface(BandedSurfaceSubmitter submitter, CrucibleRenderState state,
            CrucibleBasin.PuddleFootprint footprint, float surfaceY) {
        for (TypeBand band : state.typeBands) {
            submitter.submit(band, (ctx, sprite) -> emitLiquidSurface(ctx, footprint, surfaceY + band.lift(),
                sprite, state.rippleAmplitude));
        }
    }

    /**
     * Emits the liquid surface grid over the goo's footprint, at the
     * context's light and color, its rim held still at the footprint's edge.
     *
     * @param ctx the render context the submitter built
     * @param footprint the square the goo covers
     * @param surfaceY the liquid surface Y height
     * @param sprite the fluid texture atlas sprite
     * @param amplitude the ripple amplitude the interior vertices carry
     */
    private static void emitLiquidSurface(RenderContext ctx, CrucibleBasin.PuddleFootprint footprint,
            float surfaceY, TextureAtlasSprite sprite, float amplitude) {
        emitLiquidSurface(ctx, footprint, surfaceY, new GooRenderUtil.UvRect(
            sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()), amplitude);
    }

    /**
     * Emits the liquid surface grid over the goo's footprint on a UV rect
     * (decision puddle-touches-walls-at-a-thousand).
     *
     * @param ctx the render context the submitter built
     * @param footprint the square the goo covers
     * @param surfaceY the liquid surface Y height
     * @param uv the sprite's UV rect
     * @param amplitude the ripple amplitude the interior vertices carry
     */
    static void emitLiquidSurface(RenderContext ctx, CrucibleBasin.PuddleFootprint footprint, float surfaceY,
            GooRenderUtil.UvRect uv, float amplitude) {
        CuboidBounds goo = new CuboidBounds(footprint.min(), footprint.max(),
            footprint.min(), footprint.max(), CrucibleBasin.FLOOR_Y, surfaceY);
        ctx.liquidSurfaceGrid(goo, uv, amplitude);
    }

}
