package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.AfterimageRenderer;
import com.mercuriusxeno.goo.client.ability.AirbornWind;
import com.mercuriusxeno.goo.client.ability.BeatRings;
import com.mercuriusxeno.goo.client.ability.BlockMingleRenderer;
import com.mercuriusxeno.goo.client.ability.BoreDrill;
import com.mercuriusxeno.goo.client.ability.ChainBurnoutRenderer;
import com.mercuriusxeno.goo.client.ability.ClickRings;
import com.mercuriusxeno.goo.client.ability.CrystalShatterVisual;
import com.mercuriusxeno.goo.client.ability.FireStream;
import com.mercuriusxeno.goo.client.ability.FloatPlatform;
import com.mercuriusxeno.goo.client.ability.GhostTrailRenderer;
import com.mercuriusxeno.goo.client.ability.GlassKnives;
import com.mercuriusxeno.goo.client.ability.GroundShake;
import com.mercuriusxeno.goo.client.ability.GrowthBreeze;
import com.mercuriusxeno.goo.client.ability.HeadMarks;
import com.mercuriusxeno.goo.client.ability.HeatWaveEffect;
import com.mercuriusxeno.goo.client.ability.HeldJavelins;
import com.mercuriusxeno.goo.client.ability.HeldSweepVisual;
import com.mercuriusxeno.goo.client.ability.HoveringKnives;
import com.mercuriusxeno.goo.client.ability.LaserScorch;
import com.mercuriusxeno.goo.client.ability.LaserVisual;
import com.mercuriusxeno.goo.client.ability.LevelCursor;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.LunarTripView;
import com.mercuriusxeno.goo.client.ability.MeteorMarkers;
import com.mercuriusxeno.goo.client.ability.MeteorScorches;
import com.mercuriusxeno.goo.client.ability.MiasmaVortex;
import com.mercuriusxeno.goo.client.ability.NovaChargeGhost;
import com.mercuriusxeno.goo.client.ability.NovaRings;
import com.mercuriusxeno.goo.client.ability.PartnerMeshes;
import com.mercuriusxeno.goo.client.ability.PetrifyFog;
import com.mercuriusxeno.goo.client.ability.PneumaWind;
import com.mercuriusxeno.goo.client.ability.PortalFaces;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.ability.QuantumBlobDarts;
import com.mercuriusxeno.goo.client.ability.ReapSlashBlades;
import com.mercuriusxeno.goo.client.ability.ReapSwells;
import com.mercuriusxeno.goo.client.ability.RelayLasers;
import com.mercuriusxeno.goo.client.ability.RollingGooRenderer;
import com.mercuriusxeno.goo.client.ability.SculkerEchoRenderer;
import com.mercuriusxeno.goo.client.ability.SculkerGlassRenderer;
import com.mercuriusxeno.goo.client.ability.SculkerSenses;
import com.mercuriusxeno.goo.client.ability.SculkerVision;
import com.mercuriusxeno.goo.client.ability.SignalRings;
import com.mercuriusxeno.goo.client.ability.SpikesBristle;
import com.mercuriusxeno.goo.client.ability.SpikesRise;
import com.mercuriusxeno.goo.client.ability.Tomes;
import com.mercuriusxeno.goo.client.ability.TransformationRenderer;
import com.mercuriusxeno.goo.client.ability.WindLines;
import com.mercuriusxeno.goo.client.ability.WispGlow;
import com.mercuriusxeno.goo.client.ability.ZapBolts;
import com.mercuriusxeno.goo.client.ber.BeaconBeams;
import com.mercuriusxeno.goo.client.ber.ReflectorBeams;
import com.mercuriusxeno.goo.client.ber.style.GlowColumn;
import com.mercuriusxeno.goo.client.entity.MeteorRenderer;
import com.mercuriusxeno.goo.client.entity.WispEchoes;
import com.mercuriusxeno.goo.client.hole.HoleMouths;
import com.mercuriusxeno.goo.client.hole.HolePits;
import com.mercuriusxeno.goo.client.hud.CanisterHudRenderer;
import com.mercuriusxeno.goo.client.hud.CrucibleHudRenderer;
import com.mercuriusxeno.goo.client.hud.PlexerHudRenderer;
import com.mercuriusxeno.goo.client.hud.VatHudRenderer;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.model.GooJoins;
import com.mercuriusxeno.goo.client.overlay.AimedMobRipple;
import com.mercuriusxeno.goo.client.overlay.BlinkCursor;
import com.mercuriusxeno.goo.client.overlay.DowsingXray;
import com.mercuriusxeno.goo.client.overlay.GasketOverlayRenderer;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.client.overlay.OculusXray;
import com.mercuriusxeno.goo.client.overlay.OreXray;
import com.mercuriusxeno.goo.client.overlay.TransmitTargetMarker;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.client.zone.RoomCopyRenderer;
import com.mercuriusxeno.goo.client.zone.VoidstoneFaces;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.PrepareRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The one path every translucent goo visual drawn on a level render stage
 * draws through, so each sorts against the clouds, the weather, water, glass
 * and every other goo visual the same way. Each visual names a
 * {@link SortRoute} as it registers; the path draws each route at the stage
 * the route names, in the routes' declared order, every route's draws
 * farthest from the camera first.
 * ConventionTest.levelStageDrawsGoThroughTheSortingPath fails a client class
 * that subscribes to a level render stage itself.
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class TranslucentVisualPath {

    /** Every visual the path draws, each naming its sort route. */
    static final List<Registration> REGISTRATIONS = List.of(
            new Registration(MiasmaVortex.class, SortRoute.FAR_TO_NEAR, MiasmaVortex::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(LaserVisual.class, SortRoute.FAR_TO_NEAR, LaserVisual::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(LaserScorch.class, SortRoute.ON_OPAQUE_FACES, LaserScorch::collect),
            new Registration(RelayLasers.class, SortRoute.FAR_TO_NEAR, RelayLasers::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(ZapBolts.class, SortRoute.FAR_TO_NEAR, ZapBolts::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(FireStream.class, SortRoute.FAR_TO_NEAR, FireStream::collect),
            new Registration(ReapSlashBlades.class, SortRoute.FAR_TO_NEAR, ReapSlashBlades::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GlassKnives.class, SortRoute.FAR_TO_NEAR, GlassKnives::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(HoveringKnives.class, SortRoute.FAR_TO_NEAR, HoveringKnives::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(HeldJavelins.class, SortRoute.FAR_TO_NEAR, HeldJavelins::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(WindLines.class, SortRoute.FAR_TO_NEAR, WindLines::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(AirbornWind.class, SortRoute.FAR_TO_NEAR, AirbornWind::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(PneumaWind.class, SortRoute.FAR_TO_NEAR, PneumaWind::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GrowthBreeze.class, SortRoute.FAR_TO_NEAR, GrowthBreeze::collect,
                    Drawing.IN_THE_OIT_PHASE),
            // beaming-wisp-leaves-fading-echoes-behind-it: the echoes queue to WispGlow, so they register ahead of it
            new Registration(WispEchoes.class, SortRoute.FAR_TO_NEAR, WispEchoes::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(WispGlow.class, SortRoute.FAR_TO_NEAR, WispGlow::collect,
                    Drawing.IN_THE_OIT_PHASE),
            // operator ruling 2026-11-18: the glows vanilla's main pass drew before the clouds, queued by their
            // renderers; a prism's column registers before its beams, which sort at its center and draw after it.
            new Registration(GlowColumn.class, SortRoute.FAR_TO_NEAR, GlowColumn::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(BeaconBeams.class, SortRoute.FAR_TO_NEAR, BeaconBeams::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(ReflectorBeams.class, SortRoute.FAR_TO_NEAR, ReflectorBeams::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(MeteorRenderer.class, SortRoute.FAR_TO_NEAR, MeteorRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GloveSpecialRenderer.class, SortRoute.FAR_TO_NEAR, GloveSpecialRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(ReapSwells.class, SortRoute.FAR_TO_NEAR, ReapSwells::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GhostTrailRenderer.class, SortRoute.FAR_TO_NEAR, GhostTrailRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(SpikesBristle.class, SortRoute.OPAQUE_SHAPE, SpikesBristle::collect),
            new Registration(SpikesRise.class, SortRoute.OPAQUE_SHAPE, SpikesRise::collect),
            new Registration(BeatRings.class, SortRoute.FAR_TO_NEAR, BeatRings::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(NovaRings.class, SortRoute.FAR_TO_NEAR, NovaRings::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(NovaChargeGhost.class, SortRoute.FAR_TO_NEAR, NovaChargeGhost::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(SignalRings.class, SortRoute.FAR_TO_NEAR, SignalRings::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GroundShake.class, SortRoute.OPAQUE_SHAPE, GroundShake::collect),
            new Registration(MeteorMarkers.class, SortRoute.ON_OPAQUE_FACES, MeteorMarkers::collect),
            new Registration(MeteorScorches.class, SortRoute.ON_OPAQUE_FACES, MeteorScorches::collect),
            new Registration(PetrifyFog.class, SortRoute.FAR_TO_NEAR, PetrifyFog::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(FloatPlatform.class, SortRoute.FAR_TO_NEAR, FloatPlatform::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(CrystalShatterVisual.class, SortRoute.FAR_TO_NEAR, CrystalShatterVisual::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(HeldSweepVisual.class, SortRoute.FAR_TO_NEAR, HeldSweepVisual::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(HeldSweepVisual.class, SortRoute.THROUGH_BLOCKS, HeldSweepVisual::collectFaces),
            new Registration(LevelCursor.class, SortRoute.FAR_TO_NEAR, LevelCursor::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(HeadMarks.class, SortRoute.THROUGH_BLOCKS, HeadMarks::collect),
            // click-rings-are-the-exception-to-sculkers-vision: through blocks, after the sense view's dim
            new Registration(ClickRings.class, SortRoute.THROUGH_BLOCKS, ClickRings::collect),
            new Registration(AfterimageRenderer.class, SortRoute.FAR_TO_NEAR, AfterimageRenderer::collect),
            new Registration(BlockMingleRenderer.class, SortRoute.ON_OPAQUE_FACES, BlockMingleRenderer::collectCoat),
            new Registration(BlockMingleRenderer.class, SortRoute.FAR_TO_NEAR, BlockMingleRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(LiftedPalmBlob.class, SortRoute.FAR_TO_NEAR, LiftedPalmBlob::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(RollingGooRenderer.class, SortRoute.FAR_TO_NEAR, RollingGooRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(TransformationRenderer.class, SortRoute.FAR_TO_NEAR, TransformationRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(Tomes.class, SortRoute.FAR_TO_NEAR, Tomes::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(PartnerMeshes.class, SortRoute.OPAQUE_SHAPE, PartnerMeshes::collectSolid),
            new Registration(PartnerMeshes.class, SortRoute.FAR_TO_NEAR, PartnerMeshes::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(SculkerVision.class, SortRoute.SCREEN_SPACE, SculkerVision::collect),
            new Registration(SculkerGlassRenderer.class, SortRoute.SCREEN_SPACE, SculkerGlassRenderer::collect),
            new Registration(SculkerEchoRenderer.class, SortRoute.SCREEN_SPACE, SculkerEchoRenderer::collect),
            new Registration(HeatWaveEffect.class, SortRoute.SCREEN_SPACE, HeatWaveEffect::collect),
            new Registration(SculkerSenses.class, SortRoute.THROUGH_BLOCKS, SculkerSenses::collect),
            new Registration(LunarTripView.class, SortRoute.SKY, LunarTripView::collectSky),
            new Registration(LunarTripView.class, SortRoute.THROUGH_BLOCKS, LunarTripView::collect),
            new Registration(BoreDrill.class, SortRoute.FAR_TO_NEAR, BoreDrill::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(PortalFaces.class, SortRoute.ON_OPAQUE_FACES, PortalFaces::collectFaces),
            new Registration(PortalFaces.class, SortRoute.FAR_TO_NEAR, PortalFaces::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(PortalView.class, SortRoute.SKY, PortalView::collectSky),
            new Registration(QuantumBlobDarts.class, SortRoute.FAR_TO_NEAR, QuantumBlobDarts::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(HoleMouths.class, SortRoute.FAR_TO_NEAR, HoleMouths::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(HolePits.class, SortRoute.OPAQUE_SHAPE, HolePits::collect),
            new Registration(RoomCopyRenderer.class, SortRoute.OPAQUE_SHAPE, RoomCopyRenderer::collectSolid),
            new Registration(RoomCopyRenderer.class, SortRoute.FAR_TO_NEAR, RoomCopyRenderer::collect),
            new Registration(VoidstoneFaces.class, SortRoute.OPAQUE_SHAPE, VoidstoneFaces::collect),
            new Registration(AimedMobRipple.class, SortRoute.FAR_TO_NEAR, AimedMobRipple::collect),
            new Registration(BlinkCursor.class, SortRoute.FAR_TO_NEAR, BlinkCursor::collect),
            new Registration(DowsingXray.class, SortRoute.THROUGH_BLOCKS, DowsingXray::collect),
            new Registration(OculusXray.class, SortRoute.THROUGH_BLOCKS, OculusXray::collect),
            new Registration(OreXray.class, SortRoute.THROUGH_BLOCKS, OreXray::collect),
            new Registration(TransmitTargetMarker.class, SortRoute.THROUGH_BLOCKS, TransmitTargetMarker::collect),
            new Registration(GooFlightRenderer.class, SortRoute.FAR_TO_NEAR, GooFlightRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GooJoins.class, SortRoute.FAR_TO_NEAR, GooJoins::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(ChainBurnoutRenderer.class, SortRoute.FAR_TO_NEAR, ChainBurnoutRenderer::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GooTargetHighlighter.class, SortRoute.ON_OPAQUE_FACES,
                    GooTargetHighlighter::collectOutline),
            new Registration(GooTargetHighlighter.class, SortRoute.THROUGH_BLOCKS,
                    GooTargetHighlighter::collectThroughBlocks),
            new Registration(GooTargetHighlighter.class, SortRoute.FAR_TO_NEAR, GooTargetHighlighter::collect,
                    Drawing.IN_THE_OIT_PHASE),
            new Registration(GasketOverlayRenderer.class, SortRoute.ON_OPAQUE_FACES, GasketOverlayRenderer::collect),
            new Registration(CanisterHudRenderer.class, SortRoute.FAR_TO_NEAR, CanisterHudRenderer::collect),
            new Registration(CrucibleHudRenderer.class, SortRoute.FAR_TO_NEAR, CrucibleHudRenderer::collect),
            new Registration(PlexerHudRenderer.class, SortRoute.FAR_TO_NEAR, PlexerHudRenderer::collect),
            new Registration(VatHudRenderer.class, SortRoute.FAR_TO_NEAR, VatHudRenderer::collect));

    /** The far-to-near draws the current level pass's OIT phase draws, held for its AfterLevel. */
    private static final OitDrawReplay OIT_REPLAY = new OitDrawReplay();

    /**
     * The profiler sections the path gathers each route's draws under, each
     * draw's sort key computed as its visual hands it over, and orders them
     * under, and the counter of the draws it orders.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     */
    public static final String PATH_COLLECT_SECTION = "goo_translucent_path_collect";
    public static final String PATH_SORT_SECTION = "goo_translucent_path_sort";
    public static final String PATH_SORTED_COUNTER = "goo_translucent_path_sorted";

    /** The camera of the level pass submitting its entities now, null outside one. */
    private static @Nullable Vec3 submittingCamera;

    private TranslucentVisualPath() {
    }

    /**
     * Where the camera stands of the level pass submitting its entities,
     * block entities and items now: from the pass's opaque blocks until the
     * path draws its once-the-level-is-drawn routes. The hand's pass and the
     * GUI submit after, outside any level pass, so a renderer drawing for
     * both tells here whether its glow belongs on the path.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @return the camera's place in the world, or null outside a level pass
     */
    public static @Nullable Vec3 submittingLevelCamera() {
        return submittingCamera;
    }

    /**
     * Draws what belongs with the sky, behind everything after it.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        drawStage(event, RenderLevelStageEvent.AfterSky.class);
    }

    /**
     * Draws the decals on opaque faces, before anything translucent.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        drawStage(event, RenderLevelStageEvent.AfterOpaqueBlocks.class);
        // LevelRenderer submits the pass's entities, block entities and particles right after this stage.
        submittingCamera = event.getLevelRenderState().cameraRenderState.pos;
    }

    /**
     * Requests the index counts the OIT stages' draws use, before the stages' open passes stand.
     * decision goo-translucency-draws-through-order-independent-transparency
     *
     * @param event the buffer preparation event
     */
    @SubscribeEvent
    public static void onPrepareRenderBuffers(PrepareRenderBuffersEvent event) {
        ImmediateBuffers.requestOitIndexCounts();
    }

    /**
     * Draws, in one of 26.3's OIT stages, the far-to-near draws of the visuals that draw through ImmediateBuffers
     * alone, each in the stage's open pass through its render type's OIT pipeline for the stage; a draw whose type
     * carries no set draws at AfterLevel instead. A visual drawing through passes of its own stays at AfterLevel,
     * since a second pass cannot open inside the stage's.
     * decision goo-translucency-draws-through-order-independent-transparency
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onOitTranslucent(RenderLevelStageEvent.OitTranslucent event) {
        LevelFrame frame = new LevelFrame(event.getModelViewMatrix(), new PoseStack(), event.getLevelRenderState());
        List<SortedDraw> draws = OIT_REPLAY.atOitStage(event.getOitStage(), () -> orderedDraws(
                sourcesOf(SortRoute.FAR_TO_NEAR, Drawing.IN_THE_OIT_PHASE), frame));
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(event.getModelViewMatrix());
        StagePass.during(event.getRenderPass(), () -> OitDrawPhase.during(
                OitDrawPhase.inTheOitStage(event.getOitStage()), () -> {
                    draws.forEach(draw -> draw.draw().run());
                    ImmediateBuffers.shared().endBatch();
                }));
        modelViewStack.popMatrix();
    }

    /**
     * Draws every other registered visual once the level is drawn.
     * LevelRenderer runs the clouds pass, the weather pass and the Fabulous
     * blend after the main pass, where AfterTranslucentBlocks fires, and
     * GameRenderer posts AfterLevel once they are done, before the hand: a
     * visual blending without writing depth drawn any earlier is painted
     * over by a cloud or rain farther from the camera than it.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        submittingCamera = null;
        drawStage(event, RenderLevelStageEvent.AfterLevel.class);
    }

    // RenderSystem's model view is set to the level's while the draws run: LevelRenderer pops its view off the
    // model view stack before AfterLevel, and every render type draw reads the stack's top. AfterOpaqueBlocks holds
    // the main pass open, which every draw on it draws through.
    private static void drawStage(RenderLevelStageEvent event, Class<? extends RenderLevelStageEvent> stage) {
        RenderPass open = event instanceof RenderLevelStageEvent.AfterOpaqueBlocks opaque ? opaque.getRenderPass() : null;
        StagePass.during(open, () -> drawRoutes(event.getModelViewMatrix(), event.getLevelRenderState(),
                routesAt(stage)));
    }

    /**
     * Draws the once-the-level-is-drawn routes at the end of a portal's own
     * pass: LevelRenderer.renderLevel never posts AfterLevel, GameRenderer
     * does, so a portal view rendering the partner level through
     * renderLevel alone would draw no far-to-near visual. A post effect
     * stays with the frame, so the screen-space route draws only there.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param modelView        the portal camera's view this pass
     * @param levelRenderState the level's render state this pass
     */
    public static void drawAfterAPortalsLevel(Matrix4fc modelView, LevelRenderState levelRenderState) {
        submittingCamera = null;
        drawRoutes(modelView, levelRenderState, portalRoutes());
    }

    /**
     * The routes a portal's pass draws once its level is drawn: every route
     * at AfterLevel but the screen-space one, in declared order.
     *
     * @return the routes
     */
    static List<SortRoute> portalRoutes() {
        return routesAt(RenderLevelStageEvent.AfterLevel.class).stream()
                .filter(route -> route != SortRoute.SCREEN_SPACE).toList();
    }

    private static List<SortRoute> routesAt(Class<? extends RenderLevelStageEvent> stage) {
        return Arrays.stream(SortRoute.values()).filter(route -> route.stage() == stage).toList();
    }

    private static void drawRoutes(Matrix4fc modelView, LevelRenderState levelRenderState, List<SortRoute> routes) {
        LevelFrame frame = new LevelFrame(modelView, new PoseStack(), levelRenderState);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(modelView);
        for (SortRoute route : routes) {
            (route == SortRoute.FAR_TO_NEAR ? farToNearDrawsOf(frame) : orderedDraws(sourcesOf(route, null), frame))
                    .forEach(draw -> draw.draw().run());
        }
        ImmediateBuffers.shared().endBatch();
        modelViewStack.popMatrix();
    }

    // The far-to-near draws once the level is drawn: where the pass's OIT phase drew the immediate visuals, their held
    // draws replay beside the rest, each batch ending inside its own phase, so what carries an OIT set stays out.
    private static List<SortedDraw> farToNearDrawsOf(LevelFrame frame) {
        List<SortedDraw> held = OIT_REPLAY.takeHeld();
        if (held == null) {
            return orderedDraws(sourcesOf(SortRoute.FAR_TO_NEAR, null), frame);
        }
        List<SortedDraw> replayed = held.stream().map(draw -> new SortedDraw(draw.cameraDistanceSquared(), () -> {
            ImmediateBuffers.shared().endBatch();
            OitDrawPhase.during(OitDrawPhase.AFTER_THE_OIT_PHASE, () -> {
                draw.draw().run();
                ImmediateBuffers.shared().endBatch();
            });
        })).toList();
        return orderedDraws(sourcesOf(SortRoute.FAR_TO_NEAR, Drawing.ITS_OWN_PASSES), frame, replayed);
    }

    private static List<TranslucentVisual> sourcesOf(SortRoute route, @Nullable Drawing drawing) {
        return REGISTRATIONS.stream().filter(registration -> registration.route() == route
                && (drawing == null || registration.drawing() == drawing)).map(Registration::source).toList();
    }

    /**
     * One route's draws this frame in the order they draw: each visual's
     * draws gathered, each carrying the sort key its visual computed, then
     * ordered farthest first.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     *
     * @param sources the visuals registered on the route, in registered order
     * @param frame   the frame being drawn
     * @return the draws, farthest first
     */
    static List<SortedDraw> orderedDraws(List<? extends TranslucentVisual> sources, LevelFrame frame) {
        return orderedDraws(sources, frame, List.of());
    }

    /**
     * @param sources the visuals registered on the route, in registered order
     * @param frame   the frame being drawn
     * @param joined  draws ordered among the visuals' own, such as the OIT phase's held draws replaying
     * @return the draws, farthest first
     */
    private static List<SortedDraw> orderedDraws(List<? extends TranslucentVisual> sources, LevelFrame frame,
            List<SortedDraw> joined) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push(PATH_COLLECT_SECTION);
        List<SortedDraw> draws = new ArrayList<>(joined);
        for (TranslucentVisual source : sources) {
            source.collect(frame, draws::add);
        }
        profiler.popPush(PATH_SORT_SECTION);
        List<SortedDraw> ordered = farToNear(draws);
        profiler.pop();
        profiler.incrementCounter(PATH_SORTED_COUNTER, draws.size());
        return ordered;
    }

    /**
     * The draws in the order they blend right: each one farther from the
     * camera first, so a nearer one blends over it.
     *
     * @param draws the frame's draws, any order
     * @return the draws, farthest first
     */
    static List<SortedDraw> farToNear(List<SortedDraw> draws) {
        return draws.stream()
                .sorted(Comparator.comparingDouble(SortedDraw::cameraDistanceSquared).reversed())
                .toList();
    }

    /**
     * A visual the path draws.
     *
     * @param visual  the class that shows it
     * @param route   how it sorts
     * @param source  what hands the path its draws
     * @param drawing what its draws draw through
     */
    record Registration(Class<?> visual, SortRoute route, TranslucentVisual source, Drawing drawing) {

        /**
         * A visual whose draws may open passes of their own.
         *
         * @param visual the class that shows it
         * @param route  how it sorts
         * @param source what hands the path its draws
         */
        Registration(Class<?> visual, SortRoute route, TranslucentVisual source) {
            this(visual, route, source, Drawing.ITS_OWN_PASSES);
        }
    }

    /**
     * What a visual's draws draw through, which decides whether the path may draw them inside an OIT stage's open
     * pass, where no second pass opens and nothing writes through the command encoder.
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    enum Drawing {
        /**
         * ImmediateBuffers, or a pass of its own that draws in the stage's open pass through its set's pipeline for
         * the stage, and nothing that opens a second pass or copies a texture inside one, so a far-to-near visual's
         * draws draw in the OIT stages.
         */
        IN_THE_OIT_PHASE,
        /** Passes, targets or copies of its own that no OIT stage's open pass takes, so it draws at its route's stage. */
        ITS_OWN_PASSES
    }
}
