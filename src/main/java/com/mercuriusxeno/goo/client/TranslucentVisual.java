package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A translucent goo visual drawn by the {@link TranslucentVisualPath}: each
 * frame it hands the path one {@link SortedDraw} per batch it draws, and the
 * path runs them in the order its {@link SortRoute} names.
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 */
@FunctionalInterface
public interface TranslucentVisual {

    /**
     * Hands the path this frame's draws.
     *
     * @param frame the frame being drawn
     * @param draws where each draw goes
     */
    void collect(LevelFrame frame, Consumer<SortedDraw> draws);

    /**
     * The frame a stage draws, as the path hands it to every visual.
     *
     * @param modelView        the level's model view matrix, which the path also sets as
     *                         RenderSystem's model view while its draws run
     * @param poseStack        a camera-relative pose stack, identity at its base
     * @param levelRenderState the level's render state this frame
     */
    record LevelFrame(Matrix4fc modelView, PoseStack poseStack, LevelRenderState levelRenderState) {
    }

    /**
     * One batch a visual draws this frame. A batch of several shapes sorts
     * by its nearest shape.
     *
     * @param cameraDistanceSquared the squared distance from the camera to the batch
     * @param draw                  draws the batch, ending its buffers
     */
    record SortedDraw(double cameraDistanceSquared, Runnable draw) {

        /**
         * A batch sorted by the nearest of its shapes.
         *
         * @param shapes each shape's place in the world
         * @param camera the camera's place in the world
         * @param draw   draws the batch, ending its buffers
         * @return the draw
         */
        public static SortedDraw nearestOf(Stream<Vec3> shapes, Vec3 camera, Runnable draw) {
            return new SortedDraw(shapes.mapToDouble(camera::distanceToSqr).min().orElse(0), draw);
        }
    }
}
