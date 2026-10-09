package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.TravelingStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.entity.RollingGoo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws each rolling goo where it is this frame: its type's blob, and about
 * it the swirling nova its traveling step names, frost's Orb rolling with
 * its storm (decision orb-carries-a-swirling-nova). The entity's own
 * renderer draws nothing, so the ball and its swirl draw together here.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class RollingGooRenderer {

    private RollingGooRenderer() {
    }

    /**
     * The swirl a rolling ability carries, read off its traveling step.
     *
     * @param abilityId the rolling ability
     * @return the swirl's reach, 0 for none
     */
    static float swirlOf(String abilityId) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? 0f : TravelingStep.of(ability.behaviors()).map(TravelingStep::swirl).orElse(0f);
    }

    /**
     * Draws every rolling goo after translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = level.getGameTime() + partialTick;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RollingGoo goo && goo.gooType() != null) {
                drawGoo(level, poseStack, buffers, camera, goo, goo.getPosition(partialTick), gameTime);
            }
        }
        buffers.endBatch();
    }

    private static void drawGoo(ClientLevel level, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                Vec3 camera, RollingGoo goo, Vec3 at, float gameTime) {
        ResourceKey<GooTypeDefinition> type = goo.gooType();
        poseStack.pushPose();
        poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        GooFlightRenderer.renderBlob(poseStack, buffers, type, gameTime, 1f);
        poseStack.popPose();
        float swirl = swirlOf(goo.abilityId());
        if (swirl > 0f) {
            OrbSwirl.draw(level, poseStack, buffers, camera, at, swirl, gameTime);
        }
    }
}
