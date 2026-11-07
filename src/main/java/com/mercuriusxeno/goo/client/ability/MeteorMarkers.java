package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Meteo's blob marking its target: where the blob landed it stays, an
 * astral blob as it flew, sitting on the ground and breathing a glow
 * through the wind-up and the fall, so it paints where the meteor will
 * strike, until the strike swallows it (operator ruling 2026-11-06).
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeteorMarkers {

    /** How far below the target's cell center the ground it sits on lies. */
    private static final double TO_THE_GROUND = 0.5;
    /** The glow about the blob, at its widest breath. */
    private static final float GLOW_RADIUS = 0.45f;
    private static final float GLOW_BREATH = 0.12f;
    private static final float BREATH_RATE = 0.25f;
    private static final int GLOW_ALPHA = 60;

    private MeteorMarkers() {
    }

    /**
     * Draws a marking blob at every meteor's target once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float gameTime = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        int astral = ClientGooTypes.color(GooTypes.ASTRAL);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Meteor meteor) {
                Vec3 at = markerAt(meteor.syncedTarget()).subtract(camera);
                poseStack.pushPose();
                poseStack.translate(at.x, at.y, at.z);
                GooFlightRenderer.renderBlob(poseStack, buffers, GooTypes.ASTRAL, gameTime, 1f);
                float breath = GLOW_RADIUS + GLOW_BREATH * Mth.sin(gameTime * BREATH_RATE);
                ColorSphere.emit(poseStack.last(), buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE), Vec3.ZERO,
                        breath, ARGB.color(GLOW_ALPHA, astral));
                poseStack.popPose();
            }
        }
        buffers.endBatch();
    }

    /**
     * Where the marking blob sits: on the ground at the bottom of the cell
     * the meteor strikes, its shell resting on the face.
     *
     * @param target the point the meteor strikes, the landing cell's center
     * @return the blob's center
     */
    static Vec3 markerAt(Vec3 target) {
        return target.subtract(0, TO_THE_GROUND - GooFlightRenderer.SHELL_HW, 0);
    }
}
