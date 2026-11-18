package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.TranslucentVisualPath.Registration;
import com.mercuriusxeno.goo.client.ability.AirbornWind;
import com.mercuriusxeno.goo.client.ability.BeatRings;
import com.mercuriusxeno.goo.client.ability.CrystalShatterVisual;
import com.mercuriusxeno.goo.client.ability.FireStream;
import com.mercuriusxeno.goo.client.ability.FloatPlatform;
import com.mercuriusxeno.goo.client.ability.GlassKnives;
import com.mercuriusxeno.goo.client.ability.GroundShake;
import com.mercuriusxeno.goo.client.ability.GrowthBreeze;
import com.mercuriusxeno.goo.client.ability.HeadMarks;
import com.mercuriusxeno.goo.client.ability.HeatWaveEffect;
import com.mercuriusxeno.goo.client.ability.HeldSweepVisual;
import com.mercuriusxeno.goo.client.ability.HoveringKnives;
import com.mercuriusxeno.goo.client.ability.LaserScorch;
import com.mercuriusxeno.goo.client.ability.LaserVisual;
import com.mercuriusxeno.goo.client.ability.LevelCursor;
import com.mercuriusxeno.goo.client.ability.MeteorMarkers;
import com.mercuriusxeno.goo.client.ability.MeteorScorches;
import com.mercuriusxeno.goo.client.ability.MiasmaVortex;
import com.mercuriusxeno.goo.client.ability.NovaChargeGhost;
import com.mercuriusxeno.goo.client.ability.NovaRings;
import com.mercuriusxeno.goo.client.ability.PetrifyFog;
import com.mercuriusxeno.goo.client.ability.PneumaWind;
import com.mercuriusxeno.goo.client.ability.SignalRings;
import com.mercuriusxeno.goo.client.ability.ReapSlashBlades;
import com.mercuriusxeno.goo.client.ability.RelayLasers;
import com.mercuriusxeno.goo.client.ability.SpikesBristle;
import com.mercuriusxeno.goo.client.ability.SpikesRise;
import com.mercuriusxeno.goo.client.ability.WindLines;
import com.mercuriusxeno.goo.client.ability.WispGlow;
import com.mercuriusxeno.goo.client.ability.ZapBolts;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
     * stage a route names and no other.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Test
    void drawsAfterTheCloudsAndTheWeather() {
        Set<Class<?>> stages = Arrays.stream(TranslucentVisualPath.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .map(method -> method.getParameterTypes()[0]).collect(Collectors.toSet());
        assertAll(
                () -> assertEquals(RenderLevelStageEvent.AfterLevel.class, SortRoute.FAR_TO_NEAR.stage()),
                () -> assertEquals(Arrays.stream(SortRoute.values()).map(SortRoute::stage)
                        .collect(Collectors.toSet()), stages));
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
            assertAll(sorted.stream().map(pipeline -> () -> assertEquals(CompareOp.LESS_THAN_OR_EQUAL,
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
                            CompareOp.LESS_THAN_OR_EQUAL, pipeline.getDepthStencilState().depthTest(),
                            entry.getKey().getSimpleName() + " through " + pipeline.getLocation()))));
        }
    }
}
