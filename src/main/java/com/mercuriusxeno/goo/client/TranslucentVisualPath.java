package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.MiasmaVortex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The one path every translucent goo visual drawn on a level render stage
 * draws through, so each sorts against the clouds, the weather, water, glass
 * and every other goo visual the same way. It draws once the level is drawn:
 * first the visuals a standing decision shows through blocks, then the rest
 * farthest from the camera first, each tested against the level's depth.
 * ConventionTest.levelStageDrawsGoThroughTheSortingPath fails a client class
 * that subscribes to a level render stage itself.
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class TranslucentVisualPath {

    /** Every visual the path draws, each naming its sort route. */
    static final List<Registration> REGISTRATIONS = List.of(
            new Registration(MiasmaVortex.class, SortRoute.FAR_TO_NEAR, MiasmaVortex::collect));

    private TranslucentVisualPath() {
    }

    /**
     * Draws every registered visual once the level is drawn. LevelRenderer
     * runs the clouds pass, the weather pass and the Fabulous blend after the
     * main pass, where AfterTranslucentBlocks fires, and GameRenderer posts
     * AfterLevel once they are done, before the hand: a visual blending
     * without writing depth drawn any earlier is painted over by a cloud or
     * rain farther from the camera than it.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Matrix4fc modelView = event.getModelViewMatrix();
        drawsOf(SortRoute.THROUGH_BLOCKS, modelView).forEach(draw -> draw.draw().run());
        farToNear(drawsOf(SortRoute.FAR_TO_NEAR, modelView)).forEach(draw -> draw.draw().run());
    }

    private static List<SortedDraw> drawsOf(SortRoute route, Matrix4fc modelView) {
        List<SortedDraw> draws = new ArrayList<>();
        for (Registration registration : REGISTRATIONS) {
            if (registration.route() == route) {
                registration.source().collect(modelView, draws::add);
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
