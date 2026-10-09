package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.SunbeamPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sunbeam's ray as every client tracking the caster sees it: glow's beam of
 * light ({@link GlowBeamMesh}) from just below the caster's eye to where the
 * ray struck, and on from a prism it struck to each mob the prism refracted
 * it toward. A ray shows while its caster holds it and for a tick after the
 * last one the server sent.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SunbeamVisual {

    /** Ticks a ray keeps showing after the last one arrived. */
    static final int LINGER_TICKS = 2;
    /** The ray's layers draw at this share of the beacon's width, slimmed after UAT (operator ruling 2026-10-09). */
    static final float RAY_WIDTH = 0.35f;
    /** Where another player's glove hand sits, from the eye: below it, out to the side and ahead. */
    private static final double HAND_BELOW_EYE = 0.45;
    private static final double HAND_TO_THE_SIDE = 0.35;
    private static final double HAND_AHEAD = 0.4;
    /** Which way from the look the main hand sits: to the right, or mirrored for a left-handed player. */
    private static final double RIGHT_HANDED = 1;
    private static final double LEFT_HANDED = -1;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final double SHORTEST_SEGMENT = 1.0e-3;

    private static final Map<Integer, Ray> RAYS = new HashMap<>();

    private SunbeamVisual() {
    }

    /**
     * Handles a ray on the client thread, replacing the caster's last one.
     *
     * @param payload the ray payload
     * @param context the network context
     */
    public static void onPayload(SunbeamPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                boolean starting = !RAYS.containsKey(payload.casterId());
                RAYS.put(payload.casterId(), new Ray(payload.end(), payload.refracted(), mc.level.getGameTime()));
                Entity caster = mc.level.getEntity(payload.casterId());
                if (starting && caster != null) {
                    mc.getSoundManager().play(new SunbeamHum(caster));
                }
            }
        });
    }

    /**
     * Draws every ray still showing once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RAYS.isEmpty()) {
            RAYS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION));
        float scroll = Mth.frac(-(now + partialTick) * SCROLL_PER_TICK);
        Iterator<Map.Entry<Integer, Ray>> rays = RAYS.entrySet().iterator();
        while (rays.hasNext()) {
            Map.Entry<Integer, Ray> entry = rays.next();
            Entity caster = mc.level.getEntity(entry.getKey());
            if (caster == null || now - entry.getValue().arrivedAt() > LINGER_TICKS) {
                rays.remove();
            } else {
                Vec3 from = handOf(mc, caster, partialTick);
                drawRay(event.getPoseStack(), consumer, from.subtract(camera), entry.getValue(), camera, scroll);
            }
        }
        buffers.endBatch(GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION));
    }

    /**
     * Whether a caster's ray still shows: one arrived within the linger.
     *
     * @param casterId the caster's entity id
     * @return true while it shows
     */
    static boolean isShowing(int casterId) {
        Minecraft mc = Minecraft.getInstance();
        Ray ray = RAYS.get(casterId);
        return ray != null && mc.level != null && mc.level.getGameTime() - ray.arrivedAt() <= LINGER_TICKS;
    }

    /**
     * Where the ray leaves a caster: the goo in the local player's glove in
     * first person, else the caster's main hand, out from the eye by its look.
     *
     * @param mc          the client
     * @param caster      the casting player
     * @param partialTick the frame's partial tick
     * @return the world point
     */
    private static Vec3 handOf(Minecraft mc, Entity caster, float partialTick) {
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        }
        Vec3 look = caster.getViewVector(partialTick);
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
        double handedness = caster instanceof Player player && player.getMainArm() == HumanoidArm.LEFT
                ? LEFT_HANDED : RIGHT_HANDED;
        return caster.getEyePosition(partialTick).subtract(0, HAND_BELOW_EYE, 0)
                .add(side.scale(HAND_TO_THE_SIDE * handedness)).add(look.scale(HAND_AHEAD));
    }

    /**
     * Draws one caster's ray: from the caster to where it struck, and on to
     * each mob a prism refracted it toward.
     *
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param from      where the ray leaves the caster, camera-relative
     * @param ray       the ray
     * @param camera    the camera's position
     * @param scroll    the texture's scroll offset
     */
    private static void drawRay(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Ray ray, Vec3 camera,
                                float scroll) {
        Vec3 end = ray.end().subtract(camera);
        drawSegment(poseStack, consumer, from, end, scroll);
        for (Vec3 refracted : ray.refracted()) {
            drawSegment(poseStack, consumer, end, refracted.subtract(camera), scroll);
        }
    }

    /**
     * Draws one straight stretch of the beam between two camera-relative points.
     *
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param from      where the stretch starts, camera-relative
     * @param to        where it ends, camera-relative
     * @param scroll    the texture's scroll offset
     */
    static void drawSegment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, float scroll) {
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < SHORTEST_SEGMENT) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        poseStack.mulPose(GlowBeamMesh.alongBeam(along));
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            GlowBeamMesh.emitLayer(poseStack.last(), consumer, layer.radius() * RAY_WIDTH, layer.color(), scroll,
                    (float) length);
        }
        poseStack.popPose();
    }

    /**
     * One caster's latest ray.
     *
     * @param end       where it struck
     * @param refracted where each refracted beam ends
     * @param arrivedAt the game time it arrived
     */
    record Ray(Vec3 end, List<Vec3> refracted, long arrivedAt) {
    }
}
