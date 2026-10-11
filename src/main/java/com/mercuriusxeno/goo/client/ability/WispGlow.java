package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the wisps the block entity renderer queued this frame once the
 * translucent blocks have drawn. Block entities draw before water and other
 * translucent blocks, and a wisp writes no depth, so drawn there the water
 * behind it paints over it; drawn after, it is depth tested against the
 * water and shows in front of it, as glow's other light does.
 * decision radiant-wisps-where-light-is-low
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
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
     * Draws every wisp queued this frame, then clears the queue.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (QUEUED.isEmpty()) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE);
        PoseStack poseStack = event.getPoseStack();
        for (Sprite sprite : QUEUED) {
            poseStack.pushPose();
            poseStack.translate(sprite.center().x - camera.x, sprite.center().y - camera.y,
                    sprite.center().z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(sprite.turnDegrees()));
            sprite.draw().accept(poseStack.last(), consumer);
            poseStack.popPose();
        }
        QUEUED.clear();
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
