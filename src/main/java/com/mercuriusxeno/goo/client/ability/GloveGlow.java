package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * While right click holds a glow ability and glow goo is on hand, a soft
 * golden light glows around the glove: a disc facing the camera, added onto
 * the world, bright at the hand and fading to nothing at its rim, breathing
 * gently. It fades in as the hold starts and out as it ends, so the holder
 * sees the channel running even while no wisp leaves the hand.
 * decision radiant-wisps-where-light-is-low
 * operator ruling 2026-10-10: a light glow around the hand marks a held glow channel
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveGlow {

    /** Ticks the glow takes to fade fully in, and to fade fully out. */
    static final int FADE_TICKS = 5;
    /** The disc's radius, in blocks, before it breathes. */
    private static final float RADIUS = 0.11f;
    /** How much the radius breathes, as a share of itself, and how fast, in radians a tick. */
    private static final float BREATH = 0.12f;
    private static final float BREATH_PACE = 0.15f;
    /** The glow's alpha at the hand, at full strength: light, never a blinding flare. */
    private static final int CENTER_ALPHA = 110;
    private static final int GLOW_RGB = 0xFFE07A;
    /** Wedges the disc is drawn in around its center. */
    private static final int WEDGES = 20;

    /** Ticks the glow has faded in so far, zero to {@link #FADE_TICKS}. */
    private static int faded;
    private static int fadedBefore;

    private GloveGlow() {
    }

    /**
     * Fades the glow one tick toward shown while a glow hold runs, and toward hidden otherwise.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        fadedBefore = faded;
        faded = nextFade(faded, player != null && holdsGlow(player));
    }

    /**
     * The fade a tick moves to: one step toward full while the hold runs, one toward none otherwise.
     *
     * @param faded   ticks faded in now
     * @param holding whether a glow hold runs
     * @return ticks faded in after the tick
     */
    static int nextFade(int faded, boolean holding) {
        return Mth.clamp(holding ? faded + 1 : faded - 1, 0, FADE_TICKS);
    }

    /**
     * Whether right click holds a glow selection with glow goo on hand.
     *
     * @param player the local player
     * @return true while a glow hold runs
     */
    private static boolean holdsGlow(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        return selection != null && selection.getGooType() == GooTypes.GLOW && GloveUseTracker.runsHeld(player);
    }

    /**
     * Draws the glow around the glove after the translucent blocks, so water shows behind it.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float strength = Mth.lerp(partialTick, fadedBefore, faded) / FADE_TICKS;
        if (strength <= 0f || mc.player == null || mc.level == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 hand = GloveHand.of(mc, mc.player, partialTick).subtract(camera.position());
        float time = mc.level.getGameTime() + partialTick;
        float radius = RADIUS * (1f + BREATH * Mth.sin(time * BREATH_PACE));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(hand.x, hand.y, hand.z);
        poseStack.mulPose(camera.rotation());
        emitDisc(poseStack.last(), consumer, radius, ARGB.color(Math.round(CENTER_ALPHA * strength), GLOW_RGB));
        poseStack.popPose();
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Emits a disc in the pose's XY plane, its center the given color and its rim clear.
     *
     * @param pose     the pose at the disc's center
     * @param consumer the vertex consumer, position and color
     * @param radius   the disc's radius
     * @param center   the packed ARGB color at its center
     */
    static void emitDisc(PoseStack.Pose pose, VertexConsumer consumer, float radius, int center) {
        int rim = ARGB.color(0, center);
        for (int wedge = 0; wedge < WEDGES; wedge++) {
            float from = wedge * Mth.TWO_PI / WEDGES;
            float to = (wedge + 1) * Mth.TWO_PI / WEDGES;
            consumer.addVertex(pose, 0f, 0f, 0f).setColor(center);
            consumer.addVertex(pose, Mth.cos(from) * radius, Mth.sin(from) * radius, 0f).setColor(rim);
            consumer.addVertex(pose, Mth.cos(to) * radius, Mth.sin(to) * radius, 0f).setColor(rim);
            consumer.addVertex(pose, 0f, 0f, 0f).setColor(center);
        }
    }
}
