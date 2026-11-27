package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.RayStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.network.LaserPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Laser's ray as every client tracking the caster sees it: glow's beam of
 * light ({@link GlowBeamMesh}) from just below the caster's eye to where the
 * ray struck. A ray shows while its caster holds it and for a tick after the
 * last one the server sent. Each client tick the ray last drawn sheds light
 * motes rising out of the beam ({@link LaserMotes}) and scattering off the
 * surface it struck ({@link LaserStrike}).
 * decision every-glow-beam-breathes-in-brightness
 * decision sunbeam-sheds-motes-rising-from-the-beam
 * decision sunbeam-motes-scatter-away-from-struck-surfaces
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LaserVisual {

    /** Ticks a ray keeps showing after the last one arrived. */
    static final int LINGER_TICKS = 2;
    /** The ray's layers draw at this share of the beacon's width, slimmed after UAT (operator ruling 2026-10-09). */
    static final float RAY_WIDTH = 0.35f;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final double SHORTEST_SEGMENT = 1.0e-3;
    private static final double HALF = 0.5;
    /** How far past the server's end a sight line probes for the face another caster's ray burnt, in blocks. */
    private static final double PROBE_PAST_THE_END = 0.25;
    /** How far before a ray's end its strike probes start, in blocks. */
    private static final double PROBE_BEFORE_THE_END = 0.5;

    /** The beam's outermost layer's radius as drawn, in blocks: the motes spawn inside it. */
    private static final double DRAWN_RADIUS = GlowBeamMesh.LAYERS.getLast().radius() * RAY_WIDTH;

    private static final Map<Integer, Ray> RAYS = new HashMap<>();
    /**
     * Each showing ray as the last frame drew it, hand to end, world space:
     * the client tick sheds and scatters motes from these, so the rate
     * follows the tick and not the frame rate.
     */
    private static final Map<Integer, LaserMotes.Segment> DRAWN = new HashMap<>();

    private LaserVisual() {
    }

    /**
     * Handles a ray on the client thread, replacing the caster's last one.
     *
     * @param payload the ray payload
     * @param context the network context
     */
    public static void onPayload(LaserPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                boolean starting = !RAYS.containsKey(payload.casterId());
                RAYS.put(payload.casterId(), new Ray(payload.end(), payload.range(), mc.level.getGameTime()));
                Entity caster = mc.level.getEntity(payload.casterId());
                if (starting && caster != null) {
                    mc.getSoundManager().play(new LaserHum(caster, caster == mc.player));
                }
            }
        });
    }

    /**
     * Hands the shared path every ray still showing, sorted by its nearest caster.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the rays' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RAYS.isEmpty()) {
            RAYS.clear();
            DRAWN.clear();
            return;
        }
        ClientLevel level = mc.level;
        long now = level.getGameTime();
        RAYS.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null
                || now - entry.getValue().arrivedAt() > LINGER_TICKS);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        draws.accept(SortedDraw.nearestOf(RAYS.keySet().stream()
                        .map(level::getEntity).filter(Objects::nonNull).map(Entity::position), camera,
                () -> drawEveryRay(mc, level, frame.poseStack(), now)));
    }

    private static void drawEveryRay(Minecraft mc, ClientLevel level, PoseStack poseStack, long now) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION));
        BeamPhase phase = new BeamPhase(Mth.frac(-(now + partialTick) * SCROLL_PER_TICK),
                GlowBeamMesh.breath(now + (double) partialTick));
        drawRays(mc, level, poseStack, consumer, partialTick, phase);
        buffers.endBatch(GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION));
    }

    /**
     * Draws every ray still showing, from the caster's hand to where it ends.
     *
     * @param mc          the client
     * @param level       the level
     * @param poseStack   the pose stack
     * @param consumer    the beam's vertex consumer
     * @param partialTick the frame's partial tick
     * @param phase       the beam's scroll and breath this frame
     */
    private static void drawRays(Minecraft mc, ClientLevel level, PoseStack poseStack, VertexConsumer consumer,
                                 float partialTick, BeamPhase phase) {
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        DRAWN.clear();
        for (Map.Entry<Integer, Ray> entry : RAYS.entrySet()) {
            Entity caster = Objects.requireNonNull(level.getEntity(entry.getKey()));
            Vec3 hand = CastOrigin.of(caster.getId(), GloveHand.of(mc, caster, partialTick), partialTick);
            Vec3 end = drawnEnd(mc, caster, entry.getValue(), partialTick);
            DRAWN.put(entry.getKey(), new LaserMotes.Segment(hand, end));
            drawSegment(poseStack, consumer, hand.subtract(camera), end.subtract(camera), phase.scroll(),
                    phase.breath());
        }
    }

    /**
     * Sheds this tick's light motes out of every ray the last frame drew, and
     * scatters more off the surface each struck.
     * decision sunbeam-motes-scatter-away-from-struck-surfaces
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || DRAWN.isEmpty()) {
            return;
        }
        DRAWN.forEach((casterId, drawn) -> shedAndScatter(level, casterId, drawn));
    }

    /**
     * Sheds one ray's motes along its beam and scatters more off the surface it struck.
     *
     * @param level    the level
     * @param casterId the caster's entity id
     * @param drawn    the ray as the last frame drew it
     */
    private static void shedAndScatter(ClientLevel level, int casterId, LaserMotes.Segment drawn) {
        RandomSource random = level.getRandom();
        spawnMotes(level, LaserMotes.shedAlong(List.of(drawn), DRAWN_RADIUS, random));
        Entity caster = level.getEntity(casterId);
        if (caster == null) {
            return;
        }
        for (LaserStrike.Surface surface : LaserStrike.surfacesStruck(probeTheEnd(caster, drawn), drawn.from())) {
            spawnMotes(level, LaserStrike.scatterOff(surface, random));
        }
    }

    private static void spawnMotes(ClientLevel level, List<LaserMotes.Mote> motes) {
        for (LaserMotes.Mote mote : motes) {
            level.addParticle(GooParticles.LIGHT_MOTE.get(), mote.at().x, mote.at().y, mote.at().z,
                    mote.velocity().x, mote.velocity().y, mote.velocity().z);
        }
    }

    /**
     * Casts the nine probes across a segment's end, each parallel to the
     * beam from a little before the end to a little past it.
     *
     * @param caster  the casting entity, never struck
     * @param segment the segment whose end is probed
     * @return each probe that struck a block face or a mob
     */
    private static List<LaserStrike.Probe> probeTheEnd(Entity caster, LaserMotes.Segment segment) {
        Vec3 along = segment.to().subtract(segment.from());
        if (along.length() < SHORTEST_SEGMENT) {
            return List.of();
        }
        Vec3 axis = along.normalize();
        Vec3 before = segment.to().subtract(axis.scale(PROBE_BEFORE_THE_END));
        Vec3 past = segment.to().add(axis.scale(PROBE_PAST_THE_END));
        List<LaserStrike.Probe> probes = new ArrayList<>();
        for (Vec3 offset : LaserStrike.probeOffsets(axis, DRAWN_RADIUS)) {
            HitResult hit = RayStep.castFrom(caster.level(), caster, before.add(offset), past.add(offset),
                    living -> true);
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                probes.add(LaserStrike.Probe.onBlock(hit.getLocation(), block.getDirection()));
            } else if (hit.getType() == HitResult.Type.ENTITY) {
                probes.add(LaserStrike.Probe.onMob(hit.getLocation()));
            }
        }
        return probes;
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
        return LaserRayEnd.drawnEnd(ray.end(), ownAim(player, partialTick), ray.range(), (eye, reach) -> {
            HitResult hit = RayStep.castFrom(caster.level(), caster, eye, reach, living -> true);
            // sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
            LaserScorch.burn(caster.getId(), hit, now);
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
        LaserScorch.burn(caster.getId(), RayStep.castFrom(caster.level(), caster, eye, pastTheEnd, living -> true),
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
    private static LaserRayEnd.OwnAim ownAim(Player player, float partialTick) {
        Vec3 lockedMob = AimTracker.currentTarget() instanceof TargetResult.EntityTarget locked
                ? locked.entity().getPosition(partialTick).add(0, locked.entity().getBbHeight() * HALF, 0) : null;
        boolean locksOn = GloveThrowSender.locksOn(GloveAim.selectedAbilityId(player));
        return LaserRayEnd.ownAim(CameraEye.of(Minecraft.getInstance(), player, partialTick), locksOn, lockedMob);
    }

    /**
     * Draws one straight stretch of the beam between two camera-relative
     * points, each layer stopping flat at its end, its alpha scaled by the
     * beam's breath and its width slimmed by it.
     * decision every-glow-beam-breathes-in-brightness
     *
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param from      where the stretch starts, camera-relative
     * @param to        where it ends, camera-relative
     * @param scroll    the texture's scroll offset
     * @param breath    the share of alpha and width {@link GlowBeamMesh#breath} answered
     */
    static void drawSegment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, float scroll,
                            float breath) {
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < SHORTEST_SEGMENT) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        poseStack.rotate(GlowBeamMesh.alongBeam(along));
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            float radius = layer.radius() * RAY_WIDTH * breath;
            int color = GlowBeamMesh.breathed(layer.color(), breath);
            GlowBeamMesh.emitLayer(poseStack.last(), consumer, radius, color, scroll, (float) length);
        }
        poseStack.popPose();
    }

    /**
     * One caster's latest ray.
     *
     * @param end       where it struck
     * @param range     how far the ray reaches, in blocks
     * @param arrivedAt the game time it arrived
     */
    record Ray(Vec3 end, double range, long arrivedAt) {
    }

    /**
     * Where the beam's moving look stands this frame.
     *
     * @param scroll the texture's scroll offset in [0, 1)
     * @param breath the share of alpha and width {@link GlowBeamMesh#breath} answered
     */
    private record BeamPhase(float scroll, float breath) {
    }
}
