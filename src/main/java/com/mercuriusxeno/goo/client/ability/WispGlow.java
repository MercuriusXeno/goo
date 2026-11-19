package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws the wisps the block entity renderer queued this frame through the
 * shared path, once the level is drawn. Block entities draw before water and
 * other translucent blocks, and a wisp writes no depth, so drawn there the
 * water behind it paints over it; drawn after, it is depth tested against
 * the water and shows in front of it, as glow's other light does.
 * decision radiant-wisps-where-light-is-low
 */
public final class WispGlow {

    private static final List<Sprite> QUEUED = new ArrayList<>();

    private WispGlow() {
    }

    /**
     * Queues a wisp to draw after the translucent blocks this frame.
     *
     * @param sprite the wisp's place, turn and look
     */
    public static void queue(Sprite sprite) {
        QUEUED.add(sprite);
    }

    /**
     * Hands the shared path one draw per wisp queued this frame, each sorted
     * by its own center, then clears the queue.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where each wisp's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        if (QUEUED.isEmpty()) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        for (Sprite sprite : QUEUED) {
            draws.accept(new SortedDraw(sprite.center().distanceToSqr(camera),
                    () -> drawWisp(frame.poseStack(), sprite, camera)));
        }
        QUEUED.clear();
    }

    private static void drawWisp(PoseStack poseStack, Sprite sprite, Vec3 camera) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE);
        poseStack.pushPose();
        poseStack.translate(sprite.center().x - camera.x, sprite.center().y - camera.y,
                sprite.center().z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(sprite.turnDegrees()));
        sprite.draw().accept(poseStack.last(), consumer);
        poseStack.popPose();
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Draws a wisp's geometry about its own center.
     */
    @FunctionalInterface
    public interface Geometry {
        /**
         * Emits the wisp's quads.
         *
         * @param pose     the pose at the wisp's center, turned
         * @param consumer the vertex consumer, position and color
         */
        void accept(PoseStack.Pose pose, VertexConsumer consumer);
    }

    /**
     * A wisp queued to draw.
     *
     * @param center      its center in the world
     * @param turnDegrees its turn about the vertical, in degrees
     * @param draw        its geometry about its center
     */
    public record Sprite(Vec3 center, float turnDegrees, Geometry draw) {
    }
}
