package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
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
import com.mercuriusxeno.goo.client.ability.ReapSlashBlades;
import com.mercuriusxeno.goo.client.ability.RelayLasers;
import com.mercuriusxeno.goo.client.ability.SignalRings;
import com.mercuriusxeno.goo.client.ability.SpikesBristle;
import com.mercuriusxeno.goo.client.ability.SpikesRise;
import com.mercuriusxeno.goo.client.ability.WindLines;
import com.mercuriusxeno.goo.client.ability.WispGlow;
import com.mercuriusxeno.goo.client.ability.ZapBolts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import java.util.ArrayList;
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
            new Registration(MiasmaVortex.class, SortRoute.FAR_TO_NEAR, MiasmaVortex::collect),
            new Registration(LaserVisual.class, SortRoute.FAR_TO_NEAR, LaserVisual::collect),
            new Registration(LaserScorch.class, SortRoute.ON_OPAQUE_FACES, LaserScorch::collect),
            new Registration(RelayLasers.class, SortRoute.FAR_TO_NEAR, RelayLasers::collect),
            new Registration(ZapBolts.class, SortRoute.FAR_TO_NEAR, ZapBolts::collect),
            new Registration(FireStream.class, SortRoute.FAR_TO_NEAR, FireStream::collect),
            new Registration(ReapSlashBlades.class, SortRoute.FAR_TO_NEAR, ReapSlashBlades::collect),
            new Registration(GlassKnives.class, SortRoute.FAR_TO_NEAR, GlassKnives::collect),
            new Registration(HoveringKnives.class, SortRoute.FAR_TO_NEAR, HoveringKnives::collect),
            new Registration(WindLines.class, SortRoute.FAR_TO_NEAR, WindLines::collect),
            new Registration(AirbornWind.class, SortRoute.FAR_TO_NEAR, AirbornWind::collect),
            new Registration(PneumaWind.class, SortRoute.FAR_TO_NEAR, PneumaWind::collect),
            new Registration(GrowthBreeze.class, SortRoute.FAR_TO_NEAR, GrowthBreeze::collect),
            new Registration(HeatWaveEffect.class, SortRoute.SCREEN_SPACE, HeatWaveEffect::collect),
            new Registration(WispGlow.class, SortRoute.FAR_TO_NEAR, WispGlow::collect),
            new Registration(SpikesBristle.class, SortRoute.OPAQUE_SHAPE, SpikesBristle::collect),
            new Registration(SpikesRise.class, SortRoute.OPAQUE_SHAPE, SpikesRise::collect),
            new Registration(BeatRings.class, SortRoute.FAR_TO_NEAR, BeatRings::collect),
            new Registration(NovaRings.class, SortRoute.FAR_TO_NEAR, NovaRings::collect),
            new Registration(NovaChargeGhost.class, SortRoute.FAR_TO_NEAR, NovaChargeGhost::collect),
            new Registration(SignalRings.class, SortRoute.FAR_TO_NEAR, SignalRings::collect),
            new Registration(GroundShake.class, SortRoute.OPAQUE_SHAPE, GroundShake::collect),
            new Registration(MeteorMarkers.class, SortRoute.ON_OPAQUE_FACES, MeteorMarkers::collect),
            new Registration(MeteorScorches.class, SortRoute.ON_OPAQUE_FACES, MeteorScorches::collect),
            new Registration(PetrifyFog.class, SortRoute.FAR_TO_NEAR, PetrifyFog::collect),
            new Registration(FloatPlatform.class, SortRoute.FAR_TO_NEAR, FloatPlatform::collect),
            new Registration(CrystalShatterVisual.class, SortRoute.FAR_TO_NEAR, CrystalShatterVisual::collect),
            new Registration(HeldSweepVisual.class, SortRoute.FAR_TO_NEAR, HeldSweepVisual::collect),
            new Registration(HeldSweepVisual.class, SortRoute.THROUGH_BLOCKS, HeldSweepVisual::collectFaces),
            new Registration(LevelCursor.class, SortRoute.FAR_TO_NEAR, LevelCursor::collect),
            new Registration(HeadMarks.class, SortRoute.THROUGH_BLOCKS, HeadMarks::collect));

    private TranslucentVisualPath() {
    }

    /**
     * Draws the decals on opaque faces, before anything translucent.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        drawStage(event, RenderLevelStageEvent.AfterOpaqueBlocks.class);
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
        drawStage(event, RenderLevelStageEvent.AfterLevel.class);
    }

    // RenderSystem's model view is set to the level's while the draws run: LevelRenderer pops its view off the
    // model view stack before AfterLevel, and every render type draw reads the stack's top.
    private static void drawStage(RenderLevelStageEvent event, Class<? extends RenderLevelStageEvent> stage) {
        LevelFrame frame = new LevelFrame(event.getModelViewMatrix(), new PoseStack(), event.getLevelRenderState());
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(event.getModelViewMatrix());
        for (SortRoute route : SortRoute.values()) {
            if (route.stage() == stage) {
                farToNear(drawsOf(route, frame)).forEach(draw -> draw.draw().run());
            }
        }
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        modelViewStack.popMatrix();
    }

    private static List<SortedDraw> drawsOf(SortRoute route, LevelFrame frame) {
        List<SortedDraw> draws = new ArrayList<>();
        for (Registration registration : REGISTRATIONS) {
            if (registration.route() == route) {
                registration.source().collect(frame, draws::add);
            }
        }
        return draws;
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
     * @param visual the class that shows it
     * @param route  how it sorts
     * @param source what hands the path its draws
     */
    record Registration(Class<?> visual, SortRoute route, TranslucentVisual source) {
    }
}
