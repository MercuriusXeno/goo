package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.TranslucentVisualPath.Registration;
import com.mercuriusxeno.goo.client.ability.AfterimageRenderer;
import com.mercuriusxeno.goo.client.ability.AirbornWind;
import com.mercuriusxeno.goo.client.ability.BeatRings;
import com.mercuriusxeno.goo.client.ability.BlockMingleRenderer;
import com.mercuriusxeno.goo.client.ability.BoreDrill;
import com.mercuriusxeno.goo.client.ability.ChainBurnoutRenderer;
import com.mercuriusxeno.goo.client.ability.CrystalShatterVisual;
import com.mercuriusxeno.goo.client.ability.FireStream;
import com.mercuriusxeno.goo.client.ability.FloatPlatform;
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
import com.mercuriusxeno.goo.client.entity.WispEchoes;
import com.mercuriusxeno.goo.client.hole.HoleMouths;
import com.mercuriusxeno.goo.client.hole.HolePits;
import com.mercuriusxeno.goo.client.hud.CanisterHudRenderer;
import com.mercuriusxeno.goo.client.hud.CrucibleHudRenderer;
import com.mercuriusxeno.goo.client.hud.PlexerHudRenderer;
import com.mercuriusxeno.goo.client.hud.VatHudRenderer;
import com.mercuriusxeno.goo.client.model.GooJoins;
import com.mercuriusxeno.goo.client.overlay.AimedMobRipple;
import com.mercuriusxeno.goo.client.overlay.BlinkCursor;
import com.mercuriusxeno.goo.client.overlay.GasketOverlayRenderer;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.client.overlay.OculusXray;
import com.mercuriusxeno.goo.client.overlay.OreXray;
import com.mercuriusxeno.goo.client.overlay.TransmitTargetMarker;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.client.zone.RoomCopyRenderer;
import com.mercuriusxeno.goo.client.zone.VoidstoneFaces;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared path translucent goo visuals draw through: the stage each sort
 * route draws at, the order it draws in, and the visuals registered with it,
 * each under its route and through a pipeline that tests depth.
 */
class TranslucentVisualPathTest {

    private static final Runnable NO_DRAW = () -> { };

    private static List<SortRoute> routesOf(Class<?> visual) {
        return TranslucentVisualPath.REGISTRATIONS.stream()
                .filter(registration -> registration.visual() == visual)
                .map(Registration::route).toList();
    }

    /**
     * LevelRenderer.renderLevel adds the clouds pass, the weather pass and
     * the Fabulous blend after the main pass, and GameRenderer posts
     * AfterLevel once renderLevel returns, so a visual drawn there has the
     * clouds and weather already behind it; the path subscribes to each
     * stage a route names, and to 26.3's OIT stage with the buffer
     * preparation before it, where the far-to-near visuals moved onto order-
     * independent transparency draw, and no other.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    @Test
    void drawsAfterTheCloudsAndTheWeather() {
        Set<Class<?>> stages = Arrays.stream(TranslucentVisualPath.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .map(method -> method.getParameterTypes()[0]).collect(Collectors.toSet());
        Set<Class<?>> expected = Arrays.stream(SortRoute.values()).map(SortRoute::stage)
                .collect(Collectors.toSet());
        expected.add(RenderLevelStageEvent.OitTranslucent.class);
        expected.add(net.neoforged.neoforge.client.event.PrepareRenderBuffersEvent.class);
        assertAll(
                () -> assertEquals(RenderLevelStageEvent.AfterLevel.class, SortRoute.FAR_TO_NEAR.stage()),
                () -> assertEquals(expected, stages));
    }

    /**
     * The glow shell's immediate draws register far to near, draw through ImmediateBuffers alone, so the path draws
     * them inside the OIT stages' open pass, and draw through GLOW_SHELL_TYPE, whose set the path draws there.
     * WispEchoes queues onto WispGlow's queue as it collects, so it collects with it.
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    @Test
    void theGlowShellsImmediateDrawsDrawInTheOitPhase() {
        List<Class<?>> glowShells = List.of(LaserVisual.class, ReapSlashBlades.class, WispGlow.class,
                com.mercuriusxeno.goo.client.ber.style.GlowReflectorStyle.class,
                com.mercuriusxeno.goo.client.entity.MeteorRenderer.class, HeldSweepVisual.class,
                com.mercuriusxeno.goo.client.model.GloveSpecialRenderer.class);
        com.tngtech.archunit.core.domain.JavaClasses imported = new com.tngtech.archunit.core.importer
                .ClassFileImporter().importClasses(glowShells);
        assertAll(Stream.concat(Stream.of(() -> assertTrue(
                        PipelineReads.oitPipelines(GooRenderTypes.GLOW_SHELL_TYPE) != null, "GLOW_SHELL_TYPE's set"),
                () -> assertTrue(TranslucentVisualPath.REGISTRATIONS.stream().anyMatch(registration ->
                        registration.visual() == WispEchoes.class
                                && registration.drawing() == TranslucentVisualPath.Drawing.IMMEDIATE_BUFFERS_ALONE),
                        "WispEchoes collects with WispGlow")),
                glowShells.stream().map(visual -> () -> assertAll(
                        () -> assertTrue(TranslucentVisualPath.REGISTRATIONS.stream().anyMatch(registration ->
                                registration.visual() == visual && registration.route() == SortRoute.FAR_TO_NEAR
                                        && registration.drawing() == TranslucentVisualPath.Drawing.IMMEDIATE_BUFFERS_ALONE),
                                visual.getSimpleName() + " registers far to near, drawing through ImmediateBuffers alone"),
                        () -> assertTrue(imported.get(visual).getFieldAccessesFromSelf().stream().anyMatch(access ->
                                access.getTarget().getName().equals("GLOW_SHELL_TYPE")),
                                visual.getSimpleName() + " draws through GLOW_SHELL_TYPE")))));
    }

    /**
     * A glow prism's column draws in the OIT phase beside the Laser's core at a struck prism, which wraps it: the OIT
     * composite writes the nearest translucent depth, the core's outer layer, into the level's depth, so a column
     * left at AfterLevel failed its depth test inside the core and vanished.
     * decision goo-translucency-draws-through-order-independent-transparency
     * decision additive-glows-draw-as-translucent-layers-in-the-oit-phase
     */
    @Test
    void aGlowPrismsColumnSortsWithTheLasersCoreInTheOitPhase() {
        RegisterRenderPipelinesEvent event = org.mockito.Mockito.mock(RegisterRenderPipelinesEvent.class);
        GooRenderTypes.registerPipelines(event);
        net.minecraft.client.renderer.oit.OitPipelineSet column =
                PipelineReads.oitPipelines(GooRenderTypes.GLOW_COLUMN_TYPE);
        assertAll(
                () -> assertTrue(column != null, "GLOW_COLUMN_TYPE carries no OIT set"),
                () -> org.mockito.Mockito.verify(event).registerOitPipelineSet(column),
                () -> assertTrue(TranslucentVisualPath.REGISTRATIONS.stream().anyMatch(registration ->
                        registration.visual() == com.mercuriusxeno.goo.client.ber.style.GlowColumn.class
                                && registration.drawing() == TranslucentVisualPath.Drawing.IMMEDIATE_BUFFERS_ALONE),
                        "GlowColumn draws through ImmediateBuffers alone"));
    }

    /**
     * A decal on an opaque face draws right after the opaque blocks, ahead
     * of every block entity's beam and the Laser ray, so a beam passing in
     * front of it lies over it (operator UAT 2026-11-10).
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Test
    void decalsDrawRightAfterTheOpaqueBlocks() {
        assertEquals(RenderLevelStageEvent.AfterOpaqueBlocks.class, SortRoute.ON_OPAQUE_FACES.stage());
    }

    /**
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     */
    @Test
    void answersDrawsFarthestFromTheCameraFirst() {
        SortedDraw near = new SortedDraw(4, NO_DRAW);
        SortedDraw far = new SortedDraw(900, NO_DRAW);
        SortedDraw middle = new SortedDraw(100, NO_DRAW);
        assertEquals(List.of(far, middle, near), TranslucentVisualPath.farToNear(List.of(near, far, middle)));
    }

    /**
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     */
    @Test
    void drainsVortexDrawsFarToNear() {
        assertEquals(List.of(SortRoute.FAR_TO_NEAR), routesOf(MiasmaVortex.class));
    }

    /**
     * A portal view renders its partner level through LevelRenderer.renderLevel
     * alone, which never posts AfterLevel, so the portal's pass draws the
     * once-the-level-is-drawn routes itself, post effects aside.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     */
    @Test
    void aPortalsPassDrawsTheVisualsItsLevelHolds() {
        boolean called = new com.tngtech.archunit.core.importer.ClassFileImporter()
                .importClasses(com.mercuriusxeno.goo.client.ability.PortalView.class)
                .get(com.mercuriusxeno.goo.client.ability.PortalView.class).getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTarget().getName().equals("drawAfterAPortalsLevel")
                        && call.getTargetOwner().isEquivalentTo(TranslucentVisualPath.class));
        assertAll(
                () -> assertEquals(List.of(SortRoute.THROUGH_BLOCKS, SortRoute.FAR_TO_NEAR),
                        TranslucentVisualPath.portalRoutes()),
                () -> assertTrue(called, "PortalView draws the path's AfterLevel routes after its level"));
    }

    /**
     * Spikes goo's steel pyramids write their own depth, so they draw with
     * the opaque world, right after the opaque blocks, and every translucent
     * thing drawn later sorts against them.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     */
    @Test
    void spikesSteelDrawsWithTheOpaqueBlocks() {
        assertAll(
                () -> assertEquals(RenderLevelStageEvent.AfterOpaqueBlocks.class, SortRoute.OPAQUE_SHAPE.stage()),
                () -> assertEquals(List.of(SortRoute.OPAQUE_SHAPE), routesOf(SpikesBristle.class)),
                () -> assertEquals(List.of(SortRoute.OPAQUE_SHAPE), routesOf(SpikesRise.class)),
                () -> assertTrue(GooRenderTypes.SPIKES_STEEL.getDepthStencilState().writeDepth()));
    }

    /**
     * The goo glows vanilla's main pass drew before the clouds, which a farther
     * cloud painted over, queued by their renderers onto the path and drawn far
     * to near after the clouds (operator ruling 2026-11-18).
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Test
    void theMainPassGlowsDrawFarToNearOnThePath() {
        List<String> glows = List.of("ber.style.GlowColumn", "ber.style.GlowBeaconStyle",
                "ber.style.GlowReflectorStyle", "entity.MeteorRenderer", "ability.ReapSwells",
                "ability.GhostTrailRenderer");
        assertAll(glows.stream().map(glow -> () -> assertEquals(List.of(SortRoute.FAR_TO_NEAR),
                TranslucentVisualPath.REGISTRATIONS.stream()
                        .filter(registration -> registration.visual().getName()
                                .equals("com.mercuriusxeno.goo.client." + glow))
                        .map(Registration::route).toList(), glow)));
    }

    /**
     * The overlays, flights, joins, burnouts, highlights, gasket overlays and
     * in-world panels: the last visuals that drew on a stage of their own.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Nested
    class OverlayFlightAndJoinVisuals {

        @Test
        void eachRegistersUnderItsRoutes() {
            Map<Class<?>, Set<SortRoute>> routes = new LinkedHashMap<>();
            routes.put(AimedMobRipple.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(BlinkCursor.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(OculusXray.class, Set.of(SortRoute.THROUGH_BLOCKS));
            routes.put(OreXray.class, Set.of(SortRoute.THROUGH_BLOCKS));
            routes.put(TransmitTargetMarker.class, Set.of(SortRoute.THROUGH_BLOCKS));
            routes.put(GooFlightRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(GooJoins.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(ChainBurnoutRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(GooTargetHighlighter.class,
                    Set.of(SortRoute.ON_OPAQUE_FACES, SortRoute.THROUGH_BLOCKS, SortRoute.FAR_TO_NEAR));
            routes.put(GasketOverlayRenderer.class, Set.of(SortRoute.ON_OPAQUE_FACES));
            routes.put(CanisterHudRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(CrucibleHudRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(PlexerHudRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(VatHudRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            assertAll(routes.entrySet().stream().map(entry -> () -> assertEquals(entry.getValue(),
                    Set.copyOf(routesOf(entry.getKey())), entry.getKey().getSimpleName())));
        }

        /** Dart's held javelins and Quantum's blob darts, which the base brought drawing on a stage of their own. */
        @Test
        void theJavelinsAndDartsDrawFarToNear() {
            assertAll(
                    () -> assertEquals(List.of(SortRoute.FAR_TO_NEAR), routesOf(HeldJavelins.class)),
                    () -> assertEquals(List.of(SortRoute.FAR_TO_NEAR), routesOf(QuantumBlobDarts.class)));
        }

        /**
         * The held ghost's through-blocks pass draws before its over-blocks
         * pass, as it did inside one draw (decision held-visual-ghosts-the-landing-in-two-passes),
         * and the explosions and the ghost draw once a prism's crystal, a
         * translucent feature writing depth, stands, so it draws under an
         * explosion standing in front of it: the level is drawn by AfterLevel.
         */
        @Test
        void theHeldGhostDrawsThroughThenOverAfterThePrismsDepth() {
            List<SortRoute> afterLevel = Arrays.stream(SortRoute.values())
                    .filter(route -> route.stage() == RenderLevelStageEvent.AfterLevel.class).toList();
            assertAll(
                    () -> assertTrue(afterLevel.indexOf(SortRoute.THROUGH_BLOCKS)
                            < afterLevel.indexOf(SortRoute.FAR_TO_NEAR)),
                    () -> assertEquals(RenderLevelStageEvent.AfterLevel.class, SortRoute.FAR_TO_NEAR.stage()));
        }

        /**
         * A visual that drew only at AfterLevel never drew inside a portal
         * view's pass, which posts no AfterLevel; the path runs the AfterLevel
         * routes there too, so each keeps out of the view by its own gate.
         */
        @Test
        void visualsThatDrewOnlyAfterTheLevelKeepOutOfPortalViews() {
            List<Class<?>> afterLevelOnly = List.of(AfterimageRenderer.class, SculkerSenses.class, BoreDrill.class,
                    BlinkCursor.class, AimedMobRipple.class, GlassKnives.class, HoveringKnives.class,
                    HeldJavelins.class);
            com.tngtech.archunit.core.domain.JavaClasses imported = new com.tngtech.archunit.core.importer
                    .ClassFileImporter().importClasses(afterLevelOnly);
            assertAll(afterLevelOnly.stream().map(visual -> () -> assertTrue(imported.get(visual).getMethods().stream()
                    .filter(method -> method.getName().equals("collect"))
                    .anyMatch(method -> method.getMethodCallsFromSelf().stream().anyMatch(call ->
                            call.getTarget().getName().equals("drawing")
                                    && call.getTargetOwner().isEquivalentTo(PortalView.class))),
                    visual.getSimpleName() + ".collect gates on PortalView.drawing()")));
        }
    }

    /**
     * The portal, hole and zone visuals, each registered under its routes,
     * the far-to-near ones testing depth and the opaque shapes writing it.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Nested
    class PortalHoleAndZoneVisuals {

        @Test
        void eachRegistersUnderItsRoutes() {
            Map<Class<?>, Set<SortRoute>> routes = new LinkedHashMap<>();
            routes.put(PortalFaces.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(PortalView.class, Set.of(SortRoute.SKY));
            routes.put(HoleMouths.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(HolePits.class, Set.of(SortRoute.OPAQUE_SHAPE));
            routes.put(RoomCopyRenderer.class, Set.of(SortRoute.OPAQUE_SHAPE, SortRoute.FAR_TO_NEAR));
            routes.put(VoidstoneFaces.class, Set.of(SortRoute.OPAQUE_SHAPE));
            List<Class<?>> sky = TranslucentVisualPath.REGISTRATIONS.stream()
                    .filter(registration -> registration.route() == SortRoute.SKY).map(Registration::visual).toList();
            assertAll(Stream.concat(routes.entrySet().stream().map(entry -> () -> assertEquals(entry.getValue(),
                            Set.copyOf(routesOf(entry.getKey())), entry.getKey().getSimpleName())),
                    Stream.of(() -> assertEquals(PortalView.class, sky.getLast(), "PortalView swaps last"))));
        }

        @Test
        void sortedPartsTestDepthAndOpaqueShapesWriteIt() {
            List<RenderPipeline> sorted = List.of(GooRenderTypes.GOO_PORTAL_HALO, GooRenderTypes.GOO_PORTAL_SWIRL_BAND,
                    GooRenderTypes.GOO_PORTAL_VIEW, GooRenderTypes.GOO_PORTAL_IDLE, GooRenderTypes.PORTABLE_HOLE_MOUTH,
                    GooRenderTypes.ROOM_COPY_TRANSLUCENT);
            List<RenderPipeline> opaque = List.of(GooRenderTypes.PORTABLE_HOLE_CARVE, GooRenderTypes.PORTABLE_HOLE_PIT,
                    GooRenderTypes.ROOM_COPY_SOLID, GooRenderTypes.ROOM_COPY_CUTOUT, GooRenderTypes.VOIDSTONE);
            assertAll(Stream.concat(
                    sorted.stream().map(pipeline -> () -> assertEquals(CompareOp.GREATER_THAN_OR_EQUAL,
                            pipeline.getDepthStencilState().depthTest(), pipeline.getLocation().toString())),
                    opaque.stream().map(pipeline -> () -> assertTrue(pipeline.getDepthStencilState().writeDepth(),
                            pipeline.getLocation().toString()))));
        }
    }

    /**
     * The blob, body and sense visuals, each registered under its routes and
     * drawing through pipelines that test depth, save the parts drawn with
     * the opaque blocks, with the sky, as a post effect or through walls by decision.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Nested
    class BlobBodyAndSenseVisuals {

        @Test
        void eachRegistersUnderItsRoutes() {
            Map<Class<?>, Set<SortRoute>> routes = new LinkedHashMap<>();
            routes.put(AfterimageRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(BlockMingleRenderer.class, Set.of(SortRoute.ON_OPAQUE_FACES, SortRoute.FAR_TO_NEAR));
            routes.put(LiftedPalmBlob.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(PartnerMeshes.class, Set.of(SortRoute.OPAQUE_SHAPE, SortRoute.FAR_TO_NEAR));
            routes.put(RollingGooRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(TransformationRenderer.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(Tomes.class, Set.of(SortRoute.FAR_TO_NEAR));
            routes.put(SculkerVision.class, Set.of(SortRoute.SCREEN_SPACE));
            routes.put(SculkerGlassRenderer.class, Set.of(SortRoute.SCREEN_SPACE));
            routes.put(SculkerEchoRenderer.class, Set.of(SortRoute.SCREEN_SPACE));
            routes.put(SculkerSenses.class, Set.of(SortRoute.THROUGH_BLOCKS));
            routes.put(LunarTripView.class, Set.of(SortRoute.SKY, SortRoute.THROUGH_BLOCKS));
            routes.put(BoreDrill.class, Set.of(SortRoute.FAR_TO_NEAR));
            assertAll(routes.entrySet().stream().map(entry -> () -> assertEquals(entry.getValue(),
                    Set.copyOf(routesOf(entry.getKey())), entry.getKey().getSimpleName())));
        }

        /** The sense view dims the world first, then draws its glass, then its echoes over both. */
        @Test
        void sculkersSenseViewComposesInOrder() {
            assertEquals(List.of(SculkerVision.class, SculkerGlassRenderer.class, SculkerEchoRenderer.class),
                    TranslucentVisualPath.REGISTRATIONS.stream().map(Registration::visual)
                            .filter(visual -> visual.getSimpleName().startsWith("Sculker")
                                    && visual != SculkerSenses.class).toList());
        }

        @Test
        void eachSortedPartTestsDepthAgainstTheLevel() {
            List<RenderPipeline> sorted = List.of(RenderPipelines.ENTITY_TRANSLUCENT, RenderPipelines.LINES_TRANSLUCENT,
                    GooRenderTypes.BLOCK_MINGLE_TRANSLUCENT, GooRenderTypes.SCULKER_GLASS);
            assertAll(sorted.stream().map(pipeline -> () -> assertEquals(CompareOp.GREATER_THAN_OR_EQUAL,
                    pipeline.getDepthStencilState().depthTest(), pipeline.getLocation().toString())));
        }

        /** Bore's drill clips itself against a copy of the level's depth in its shader, clouds included. */
        @Test
        void boresDrillReadsTheLevelsDepth() {
            assertTrue(PipelineReads.samplers(GooRenderTypes.BORE_DRILL.getBindGroupLayouts()).contains(GooRenderTypes.BORE_DRILL_DEPTH_SAMPLER));
        }

        @Test
        void theSkyAndTheSenseViewDrawWhereTheirRoutesSay() {
            List<SortRoute> afterLevel = Arrays.stream(SortRoute.values())
                    .filter(route -> route.stage() == RenderLevelStageEvent.AfterLevel.class).toList();
            assertAll(
                    () -> assertEquals(RenderLevelStageEvent.AfterSky.class, SortRoute.SKY.stage()),
                    () -> assertEquals(SortRoute.SCREEN_SPACE, afterLevel.get(0)));
        }
    }

    /**
     * The ring, marker and ground visuals, each registered under its routes
     * and drawing through pipelines that test depth, save the parts a
     * standing ruling shows through blocks.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Nested
    class RingMarkerAndGroundVisuals {

        @Test
        void eachRegistersUnderItsRoutes() {
            Map<Class<?>, List<SortRoute>> routes = new LinkedHashMap<>();
            routes.put(BeatRings.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(NovaRings.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(NovaChargeGhost.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(SignalRings.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(GroundShake.class, List.of(SortRoute.OPAQUE_SHAPE));
            routes.put(MeteorMarkers.class, List.of(SortRoute.ON_OPAQUE_FACES));
            routes.put(MeteorScorches.class, List.of(SortRoute.ON_OPAQUE_FACES));
            routes.put(PetrifyFog.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(FloatPlatform.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(CrystalShatterVisual.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(HeldSweepVisual.class, List.of(SortRoute.FAR_TO_NEAR, SortRoute.THROUGH_BLOCKS));
            routes.put(LevelCursor.class, List.of(SortRoute.FAR_TO_NEAR));
            routes.put(HeadMarks.class, List.of(SortRoute.THROUGH_BLOCKS));
            assertAll(routes.entrySet().stream().map(entry -> () -> assertEquals(Set.copyOf(entry.getValue()),
                    Set.copyOf(routesOf(entry.getKey())), entry.getKey().getSimpleName())));
        }

        @Test
        void eachSortedPartTestsDepthAgainstTheLevel() {
            List<RenderPipeline> sorted = List.of(GooRenderTypes.LINES_ADDITIVE_GLOW, GooRenderTypes.FROST_EXPLOSION,
                    GooRenderTypes.METEOR_SCORCH, GooRenderTypes.PETRIFY_FOG, GooRenderTypes.CRYSTAL_SHARD,
                    GooRenderTypes.GLOW_SHELL, GooRenderTypes.ROCK_EXPLOSION);
            assertAll(sorted.stream().map(pipeline -> () -> assertEquals(CompareOp.GREATER_THAN_OR_EQUAL,
                    pipeline.getDepthStencilState().depthTest(), pipeline.getLocation().toString())));
        }

        /**
         * Scry's revealed faces show through walls (decision
         * held-sweeps-share-one-fast-shell-visual), and so do the head marks,
         * so a mark never clips into the wall it stands near (operator
         * ruling 2026-11-18).
         */
        @Test
        void theThroughBlocksPartsPassEveryDepthTest() {
            assertAll(
                    () -> assertEquals(CompareOp.ALWAYS_PASS, GooRenderTypes.SCRY_FACES.getDepthStencilState().depthTest()),
                    () -> assertTrue(RenderPipelines.TEXT_SEE_THROUGH.getDepthStencilState() == null));
        }
    }

    /**
     * The beam, bolt and wind visuals, each registered under its route and
     * drawing through pipelines that test depth.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Nested
    class BeamBoltAndWindVisuals {

        /** Each visual's route and the pipelines it draws through. */
        private static final Map<Class<?>, Route> ROUTES = routes();

        private record Route(SortRoute route, List<RenderPipeline> pipelines) {
        }

        private static Map<Class<?>, Route> routes() {
            Map<Class<?>, Route> routes = new LinkedHashMap<>();
            routes.put(LaserVisual.class, sorted(GooRenderTypes.GLOW_BEAM, GooRenderTypes.GLOW_SHELL));
            routes.put(LaserScorch.class, new Route(SortRoute.ON_OPAQUE_FACES,
                    List.of(GooRenderTypes.LASER_SCORCH, GooRenderTypes.LASER_SCORCH_GLOW)));
            routes.put(RelayLasers.class, sorted(GooRenderTypes.LINES_ADDITIVE_GLOW));
            routes.put(ZapBolts.class, sorted(GooRenderTypes.LINES_ADDITIVE_GLOW));
            routes.put(FireStream.class, sorted(GooRenderTypes.FIRE_FIELD));
            routes.put(ReapSlashBlades.class, sorted(GooRenderTypes.GLOW_SHELL));
            routes.put(GlassKnives.class, sorted(GooRenderTypes.CRYSTAL_SHARD));
            routes.put(HoveringKnives.class, sorted(GooRenderTypes.CRYSTAL_SHARD));
            routes.put(WindLines.class, sorted(RenderPipelines.LINES_TRANSLUCENT));
            routes.put(AirbornWind.class, sorted(RenderPipelines.LINES_TRANSLUCENT));
            routes.put(PneumaWind.class, sorted(RenderPipelines.LINES_TRANSLUCENT));
            routes.put(GrowthBreeze.class, sorted(GooRenderTypes.GROWTH_BREEZE));
            routes.put(HeatWaveEffect.class, new Route(SortRoute.SCREEN_SPACE, List.of()));
            routes.put(WispGlow.class, sorted(GooRenderTypes.GLOW_SHELL));
            routes.put(WispEchoes.class, sorted(GooRenderTypes.GLOW_SHELL));
            return routes;
        }

        private static Route sorted(RenderPipeline... pipelines) {
            return new Route(SortRoute.FAR_TO_NEAR, List.of(pipelines));
        }

        @Test
        void eachRegistersUnderItsRoute() {
            assertAll(ROUTES.entrySet().stream().map(entry -> () -> assertEquals(List.of(entry.getValue().route()),
                    routesOf(entry.getKey()), entry.getKey().getSimpleName())));
        }

        @Test
        void eachSortedOneTestsDepthAgainstTheLevel() {
            assertAll(ROUTES.entrySet().stream()
                    .flatMap(entry -> entry.getValue().pipelines().stream().map(pipeline -> () -> assertEquals(
                            CompareOp.GREATER_THAN_OR_EQUAL, pipeline.getDepthStencilState().depthTest(),
                            entry.getKey().getSimpleName() + " through " + pipeline.getLocation()))));
        }
    }
}
