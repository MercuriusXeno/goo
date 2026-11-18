package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.TranslucentVisualPath.Registration;
import com.mercuriusxeno.goo.client.ability.AirbornWind;
import com.mercuriusxeno.goo.client.ability.FireStream;
import com.mercuriusxeno.goo.client.ability.GlassKnives;
import com.mercuriusxeno.goo.client.ability.GrowthBreeze;
import com.mercuriusxeno.goo.client.ability.HeatWaveEffect;
import com.mercuriusxeno.goo.client.ability.HoveringKnives;
import com.mercuriusxeno.goo.client.ability.LaserScorch;
import com.mercuriusxeno.goo.client.ability.LaserVisual;
import com.mercuriusxeno.goo.client.ability.MiasmaVortex;
import com.mercuriusxeno.goo.client.ability.PneumaWind;
import com.mercuriusxeno.goo.client.ability.ReapSlashBlades;
import com.mercuriusxeno.goo.client.ability.RelayLasers;
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
