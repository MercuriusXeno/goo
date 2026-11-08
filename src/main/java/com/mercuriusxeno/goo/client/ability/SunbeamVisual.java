package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.RayStep;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.network.SunbeamPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sunbeam's ray as every client tracking the caster sees it: glow's beam of
 * light ({@link GlowBeamMesh}) from just below the caster's eye to where the
 * ray struck, and on from a prism it struck to each mob the prism refracted
 * it toward. A ray shows while its caster holds it and for a tick after the
 * last one the server sent. A ray a prism refracts runs into the prism's
 * middle and its refracted beams leave from there, out of a soft core of
 * light ({@link GlowCore}).
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SunbeamVisual {

    /** Ticks a ray keeps showing after the last one arrived. */
    static final int LINGER_TICKS = 2;
    /** The ray's layers draw at this share of the beacon's width, slimmed after UAT (operator ruling 2026-10-09). */
    static final float RAY_WIDTH = 0.35f;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final double SHORTEST_SEGMENT = 1.0e-3;
    private static final double HALF = 0.5;
    /** How far past the server's end a sight line probes for the face another caster's ray burnt, in blocks. */
    private static final double PROBE_PAST_THE_END = 0.25;

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
                RAYS.put(payload.casterId(), new Ray(payload.end(), payload.refracted(), payload.range(),
                        mc.level.getGameTime()));
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
        ClientLevel level = mc.level;
        long now = level.getGameTime();
        RAYS.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null
                || now - entry.getValue().arrivedAt() > LINGER_TICKS);
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION));
        float scroll = Mth.frac(-(now + partialTick) * SCROLL_PER_TICK);
        List<Vec3> cores = new ArrayList<>();
        for (Map.Entry<Integer, Ray> entry : RAYS.entrySet()) {
            Entity caster = Objects.requireNonNull(level.getEntity(entry.getKey()));
            Vec3 hand = GloveHand.of(mc, caster, partialTick);
            Vec3 struck = drawnEnd(mc, caster, entry.getValue(), partialTick);
            Vec3 gathered = SunbeamRayEnd.gathersAt(hand, struck, pos -> refractsAt(level, pos));
            if (!gathered.equals(struck)) {
                cores.add(gathered.subtract(camera));
            }
            drawRay(event.getPoseStack(), consumer, hand.subtract(camera), gathered.subtract(camera),
                    entry.getValue(), camera, scroll);
        }
        buffers.endBatch(GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION));
        drawCores(event.getPoseStack(), buffers, cores);
    }

    /**
     * Whether a prism that refracts a ray stands at a position: one holding a combo.
     *
     * @param level the level
     * @param pos   the position
     * @return true for a refracting prism
     */
    private static boolean refractsAt(ClientLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PrismBlockEntity prism && prism.hasCombo();
    }

    /**
     * Draws the core of light at each prism a ray runs into, after the rays.
     *
     * @param poseStack the pose stack
     * @param buffers   the buffer source
     * @param cores     each core's middle, camera-relative
     */
    private static void drawCores(PoseStack poseStack, MultiBufferSource.BufferSource buffers, List<Vec3> cores) {
        if (cores.isEmpty()) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE);
        for (Vec3 core : cores) {
            poseStack.pushPose();
            poseStack.translate(core.x, core.y, core.z);
            GlowCore.emit(poseStack.last(), consumer, 1f, 1f);
            poseStack.popPose();
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
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
     * Where a caster's ray ends this frame: the local caster's own ray cast
     * from its eye along this frame's aim, another caster's where the server struck.
     * decision diagnose-and-correct-the-sunbeam-jitter
     *
     * @param mc          the client
     * @param caster      the casting entity
     * @param ray         the caster's latest ray from the server
     * @param partialTick the frame's partial tick
     * @return the world point the ray ends at
     */
    private static Vec3 drawnEnd(Minecraft mc, Entity caster, Ray ray, float partialTick) {
        double now = caster.level().getGameTime() + (double) partialTick;
        if (caster != mc.player || !(caster instanceof Player player)) {
            burnWhereTheServerStruck(caster, ray, partialTick, now);
            return ray.end();
        }
        return SunbeamRayEnd.drawnEnd(ray.end(), ownAim(player, partialTick), ray.range(), (eye, reach) -> {
            HitResult hit = RayStep.castFrom(caster.level(), caster, eye, reach, living -> true);
            // sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
            SunbeamScorch.burn(caster.getId(), hit, now);
            return hit.getLocation();
        });
    }

    /**
     * Burns the spot another caster's ray struck into its scorch: the server
     * sends where the ray stopped and not what it stopped on, so a sight line
     * from the caster's eye just past that point finds the face it burnt.
     * decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
     *
     * @param caster      the casting entity
     * @param ray         the caster's latest ray from the server
     * @param partialTick the frame's partial tick
     * @param now         the game time, in ticks, with the frame's partial tick
     */
    private static void burnWhereTheServerStruck(Entity caster, Ray ray, float partialTick, double now) {
        Vec3 eye = caster.getEyePosition(partialTick);
        Vec3 toEnd = ray.end().subtract(eye);
        if (toEnd.lengthSqr() < SHORTEST_SEGMENT) {
            return;
        }
        Vec3 pastTheEnd = ray.end().add(toEnd.normalize().scale(PROBE_PAST_THE_END));
        SunbeamScorch.burn(caster.getId(), RayStep.castFrom(caster.level(), caster, eye, pastTheEnd, living -> true),
                now);
    }

    /**
     * The local caster's aim this frame: the mob the aim assist locks, for an
     * ability that locks on, else along the look, the way its held ticks aim
     * (decision sunbeam-lands-with-impact-and-aim).
     *
     * @param player      the local player
     * @param partialTick the frame's partial tick
     * @return the aim
     */
    private static SunbeamRayEnd.OwnAim ownAim(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 lockedMob = AimTracker.currentTarget() instanceof TargetResult.EntityTarget locked
                ? locked.entity().getPosition(partialTick).add(0, locked.entity().getBbHeight() * HALF, 0) : null;
        boolean locksOn = GloveThrowSender.locksOn(GloveAim.selectedAbilityId(player));
        return new SunbeamRayEnd.OwnAim(eye,
                ChannelAim.heldAimPoint(locksOn, lockedMob, eye.add(player.getViewVector(partialTick))));
    }

    /**
     * Draws one caster's ray: from the caster to where it ends, and on to
     * each mob a prism refracted it toward.
     *
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param from      where the ray leaves the caster, camera-relative
     * @param end       where the ray ends, camera-relative
     * @param ray       the ray
     * @param camera    the camera's position
     * @param scroll    the texture's scroll offset
     */
    private static void drawRay(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 end, Ray ray,
                                Vec3 camera, float scroll) {
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
     * @param range     how far the ray reaches, in blocks
     * @param arrivedAt the game time it arrived
     */
    record Ray(Vec3 end, List<Vec3> refracted, double range, long arrivedAt) {
    }
}
