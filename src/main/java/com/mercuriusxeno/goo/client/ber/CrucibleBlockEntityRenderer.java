package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.client.BandedSurfaceSubmitter;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mercuriusxeno.goo.client.SurfaceRipple;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the crucible's liquid level surface.
 */
public class CrucibleBlockEntityRenderer
        implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderState> {

    /** The sprite-relative UV rect spanning a whole sprite. */
    private static final GooRenderUtil.UvRect WHOLE_SPRITE = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);

    /** Each crucible's surface agitation, held client-side and dropped with the crucible. */
    private final Map<CrucibleBlockEntity, SurfaceAgitation> agitations = new WeakHashMap<>();

    /** Draws the items melting on the fill. */
    private final CrucibleMeltingItems meltingItems;

    /**
     * Creates a crucible BER.
     *
     * @param context the renderer provider context, whose item model resolver the melting items draw with
     */
    public CrucibleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.meltingItems = new CrucibleMeltingItems(context.itemModelResolver());
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
        extractRipple(be, state, partialTick);
        meltingItems.extract(be, state, partialTick);
    }

    /**
     * Ticks this crucible's surface agitation on its fill, which moves as an
     * item dissolves into the pool (decision undulating-fluid-surface), and reads the
     * day fraction the ripple runs on, so tiles bob in step with the surface
     * (decision each-tile-bobs-with-the-ripple).
     *
     * @param be the crucible block entity
     * @param state the render state, pool volumes already extracted
     * @param partialTick the fraction of the tick elapsed
     */
    private void extractRipple(CrucibleBlockEntity be, CrucibleRenderState state, float partialTick) {
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        float fill = CrucibleBasin.heightFraction(state.volumes.reservoir());
        state.rippleAmplitude = agitations.computeIfAbsent(be, key -> new SurfaceAgitation())
            .tick(fill, 0f, gameTick);
        state.dayFraction = SurfaceRipple.dayFraction(gameTick, partialTick);
    }

    /**
     * Copies the volumes and the reservoir's type bands from the block entity.
     *
     * @param be the crucible block entity
     * @param state the render state to populate
     */
    private static void extractPoolState(CrucibleBlockEntity be, CrucibleRenderState state) {
        state.volumes = be.basinVolumes();
        state.typeBands = TypeBands.over(be.getReservoir());
    }

    @Override
    public void submit(CrucibleRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        CrucibleBasin.DrawnSurface surface = surfaceOf(state);
        if (surface != null) {
            renderMingledSurface(GooSubmitter.bandedSurfaces(poseStack, nodeCollector), state,
                surface.footprint(), surface.surfaceY());
        }
        meltingItems.submit(state, surface, poseStack, nodeCollector);
    }

    /**
     * Answers the surface the crucible draws, from its reservoir alone, so the basin
     * floor shows under an item until its first goo melts (decision reservoir-volume-drives-fill).
     *
     * @param state the crucible render state
     * @return the surface, or null while the reservoir is empty
     */
    static CrucibleBasin.@Nullable DrawnSurface surfaceOf(CrucibleRenderState state) {
        return CrucibleBasin.drawnSurface(state.volumes);
    }

    // -- Liquid level --

    /**
     * Submits the liquid surface once, carrying every type band, so the
     * shader mingles the types by noise on one flat surface (decisions
     * mingling-on-vat-and-crucible and one-pass-blends-every-type). The
     * submitter lights the surface fullbright; the basin model uses world light.
     *
     * @param submitter submits the surface carrying every band
     * @param state the crucible render state
     * @param footprint the square the goo covers
     * @param surfaceY the computed liquid surface Y height
     */
    static void renderMingledSurface(BandedSurfaceSubmitter submitter, CrucibleRenderState state,
            CrucibleBasin.PuddleFootprint footprint, float surfaceY) {
        submitter.submit(state.typeBands,
            ctx -> emitLiquidSurface(ctx, footprint, surfaceY, WHOLE_SPRITE, state.rippleAmplitude));
    }

    /**
     * Emits the liquid surface grid over the goo's footprint on a UV rect
     * (decision puddle-touches-walls-at-a-thousand).
     *
     * @param ctx the render context the submitter built
     * @param footprint the square the goo covers
     * @param surfaceY the liquid surface Y height
     * @param uv the UV rect, sprite-relative on the one-pass surface
     * @param amplitude the ripple amplitude the interior vertices carry
     */
    static void emitLiquidSurface(RenderContext ctx, CrucibleBasin.PuddleFootprint footprint, float surfaceY,
            GooRenderUtil.UvRect uv, float amplitude) {
        CuboidBounds goo = new CuboidBounds(footprint.min(), footprint.max(),
            footprint.min(), footprint.max(), CrucibleBasin.FLOOR_Y, surfaceY);
        ctx.liquidSurfaceGrid(goo, uv, amplitude);
    }

}
