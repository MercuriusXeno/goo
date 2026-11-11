package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.hud.InWorldHud;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Optional;

/**
 * Floats a mark over the head of every marked mob in sight, turned to face
 * the camera and bobbing gently, for as long as the mark holds: the charmed
 * heart over a charmed mob, the rally fist over a rallied one.
 * charm-glisten-and-icon-over-the-head
 * charm-holds-until-struck
 * rallied-mobs-wear-a-red-clenched-fist
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeadMarks {

    /** Half the mark's width, in blocks. */
    private static final float HALF_SIZE = 0.2f;
    /** How far above the mob's head the mark floats, in blocks. */
    private static final double ABOVE_HEAD = 0.45;
    /** How far the mark bobs up and down, in blocks. */
    private static final double BOB_HEIGHT = 0.05;
    /** The bob's pace, radians per game tick. */
    private static final double BOB_PER_TICK = 0.12;
    /** The farthest a mark draws from the camera, in blocks. */
    private static final double SIGHT_RANGE = 48.0;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private HeadMarks() {
    }

    /**
     * Draws each mark over the mobs wearing it after the translucent world,
     * one batch per mark texture.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = mc.level.getGameTime() + partialTick;
        Camera camera = mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (HeadMark mark : HeadMark.values()) {
            RenderType icon = RenderTypes.text(mark.texture());
            VertexConsumer consumer = buffers.getBuffer(icon);
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (wears(entity, mark)) {
                    drawMark(event.getPoseStack(), consumer, camera, (LivingEntity) entity, partialTick, gameTime);
                }
            }
            buffers.endBatch(icon);
        }
    }

    private static boolean wears(Entity entity, HeadMark mark) {
        return entity instanceof LivingEntity living && !living.isInvisible()
                && markOf(living).filter(mark::equals).isPresent();
    }

    private static Optional<HeadMark> markOf(LivingEntity living) {
        return HeadMark.of(living.hasData(GooAttachments.CHARMED), living.hasData(GooAttachments.RALLIED));
    }

    private static void drawMark(PoseStack poseStack, VertexConsumer consumer, Camera camera, LivingEntity living,
                                 float partialTick, float gameTime) {
        Vec3 above = living.getPosition(partialTick)
                .add(0, living.getBbHeight() + ABOVE_HEAD + bob(gameTime, living.getId()), 0)
                .subtract(camera.position());
        if (above.lengthSqr() > SIGHT_RANGE * SIGHT_RANGE) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(above.x, above.y, above.z);
        poseStack.mulPose(camera.rotation());
        PoseStack.Pose pose = poseStack.last();
        InWorldHud.iconVertex(consumer, pose, -HALF_SIZE, HALF_SIZE, 0f, 0f, 0f, OPAQUE_WHITE);
        InWorldHud.iconVertex(consumer, pose, -HALF_SIZE, -HALF_SIZE, 0f, 0f, 1f, OPAQUE_WHITE);
        InWorldHud.iconVertex(consumer, pose, HALF_SIZE, -HALF_SIZE, 0f, 1f, 1f, OPAQUE_WHITE);
        InWorldHud.iconVertex(consumer, pose, HALF_SIZE, HALF_SIZE, 0f, 1f, 0f, OPAQUE_WHITE);
        poseStack.popPose();
    }

    /**
     * The mark's bob above its resting height, each mob's mark at its own phase.
     *
     * @param gameTime the game time including the partial tick
     * @param entityId the mob's id, which sets its phase
     * @return the offset, in blocks
     */
    private static double bob(float gameTime, int entityId) {
        return Mth.sin((float) (gameTime * BOB_PER_TICK) + entityId) * BOB_HEIGHT;
    }
}
