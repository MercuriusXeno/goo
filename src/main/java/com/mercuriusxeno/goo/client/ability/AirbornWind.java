package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Airborn's look: floaty motes of air, really soft wind lines, rise straight
 * up around the player's body, each turning a small, slow, gentle swirl as it
 * climbs and fading in and out. Some rise in front of the camera, one leaving
 * often enough that at least two stand in view at once, so a first-person
 * player always sees the effect. The lines ride along with the player.
 * airborn-steerable-levitation-and-soft-falls
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AirbornWind {

    /** Ticks a mote lives, from leaving to fading out. */
    static final int LIFE_TICKS = 60;
    /** Blocks a mote rises over its life. */
    static final double RISE = 1.6;
    /** The radius of a mote's swirl, in blocks: small. */
    static final double SWIRL_RADIUS = 0.12;
    /** How fast a mote's swirl turns, in radians a tick: slow. */
    static final double SWIRL_RATE = 0.08;
    /** Ticks between motes leaving around the body. */
    static final int BODY_EVERY = 8;
    /** Ticks between motes leaving in front of the camera: a life holds at least two. */
    static final int FRONT_EVERY = 20;
    /** How far from the body's middle a body mote rises, in blocks. */
    static final double BODY_RADIUS = 0.7;
    /** How far ahead of the eye a front mote rises, in blocks, and how far to either side it may stray. */
    static final double FRONT_REACH = 1.1;
    static final double FRONT_STRAY = 0.35;
    /** How far below the eye a front mote leaves, so it rises up through the view. */
    static final double FRONT_DROP = 0.9;
    /** Ticks of the path a mote's tail trails behind its head. */
    static final double TAIL_TICKS = 12;
    private static final int TAIL_SAMPLES = 12;
    /** The share of its life a mote fades in over, and out over. */
    private static final double FADE_SHARE = 0.25;
    private static final int MAX_ALPHA = 110;
    private static final int WHITE = 0xF4F8FF;
    private static final float LINE_WIDTH = 2.5f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    /** A unit random spread across both sides, -1 to 1. */
    private static final double BOTH_SIDES = 2;

    /**
     * One mote of rising air.
     *
     * @param start     where it leaves, relative to the player's feet
     * @param phase     where its swirl starts, in radians
     * @param startTick the game time it left
     */
    record Mote(Vec3 start, double phase, long startTick) {
    }

    private static final List<Mote> LIVE = new ArrayList<>();
    private static long lastSpawnTick = -1;

    private AirbornWind() {
    }

    /**
     * Where a mote's head stands a number of ticks after it left, relative to
     * the player's feet: risen evenly, turned about its swirl.
     *
     * @param mote the mote
     * @param age  ticks since it left
     * @return the head's position relative to the feet
     */
    static Vec3 motePoint(Mote mote, double age) {
        double clamped = Math.clamp(age, 0, LIFE_TICKS);
        double turn = mote.phase() + clamped * SWIRL_RATE;
        return mote.start().add(Math.cos(turn) * SWIRL_RADIUS, RISE * clamped / LIFE_TICKS,
                Math.sin(turn) * SWIRL_RADIUS);
    }

    /**
     * A mote's opacity over its life: fading in, holding, then fading out.
     *
     * @param life the share of its life lived, 0 to 1
     * @return the opacity, 0 to 1
     */
    static double opacity(double life) {
        return Math.clamp(Math.min(life, 1 - life) / FADE_SHARE, 0, 1);
    }

    /**
     * Where a front mote leaves, relative to the feet: ahead of the eye along
     * the level look, strayed to one side, below the eye so it rises up
     * through the view.
     *
     * @param eyeHeight the player's eye height
     * @param look      the player's look
     * @param stray     the share of the stray to one side, -1 to 1
     * @return the start relative to the feet
     */
    static Vec3 frontStart(double eyeHeight, Vec3 look, double stray) {
        Vec3 level = new Vec3(look.x, 0, look.z);
        Vec3 ahead = level.lengthSqr() > 0 ? level.normalize() : new Vec3(0, 0, 1);
        Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
        return ahead.scale(FRONT_REACH).add(side.scale(stray * FRONT_STRAY)).add(0, eyeHeight - FRONT_DROP, 0);
    }

    /**
     * Where a body mote leaves, relative to the feet: on a ring about the
     * body's middle, low on the body.
     *
     * @param about  the angle about the body, in radians
     * @param height how high on the body it leaves, in blocks
     * @return the start relative to the feet
     */
    static Vec3 bodyStart(double about, double height) {
        return new Vec3(Math.cos(about) * BODY_RADIUS, height, Math.sin(about) * BODY_RADIUS);
    }

    /**
     * Spawns this tick's motes, drops spent ones and draws the rest while
     * the local player stands under Airborn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            LIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        if (player.getData(GooAttachments.AIRBORN).standsAt(now)) {
            spawn(player, now);
        }
        LIVE.removeIf(mote -> now - mote.startTick() >= LIFE_TICKS);
        if (!LIVE.isEmpty()) {
            drawMotes(event, mc, player, now);
        }
    }

    private static void drawMotes(RenderLevelStageEvent.AfterTranslucentBlocks event, Minecraft mc,
                                  LocalPlayer player, long now) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 feet = player.getPosition(partialTick);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(type));
        for (Mote mote : LIVE) {
            drawMote(lines, camera, feet, mote, now + partialTick - mote.startTick());
        }
        buffers.endBatch(type);
    }

    private static void spawn(LocalPlayer player, long now) {
        if (now == lastSpawnTick) {
            return;
        }
        lastSpawnTick = now;
        RandomSource random = player.getRandom();
        if (now % BODY_EVERY == 0) {
            LIVE.add(new Mote(bodyStart(random.nextDouble() * TWO_PI, random.nextDouble() * player.getBbHeight() * HALF),
                    random.nextDouble() * TWO_PI, now));
        }
        if (now % FRONT_EVERY == 0) {
            double stray = random.nextDouble() * BOTH_SIDES - 1;
            LIVE.add(new Mote(frontStart(player.getEyeHeight(), player.getLookAngle(), stray),
                    random.nextDouble() * TWO_PI, now));
        }
    }

    private static void drawMote(LineContext lines, Vec3 camera, Vec3 feet, Mote mote, double age) {
        int alpha = (int) Math.round(MAX_ALPHA * opacity(age / LIFE_TICKS));
        if (alpha <= 0) {
            return;
        }
        Vec3 previous = feet.add(motePoint(mote, age));
        for (int sample = 1; sample <= TAIL_SAMPLES; sample++) {
            double tail = (double) sample / TAIL_SAMPLES;
            Vec3 next = feet.add(motePoint(mote, age - tail * TAIL_TICKS));
            int color = ARGB.color((int) Math.round(alpha * (1 - tail)), WHITE);
            lines.emitPolyline(camera, new Vec3[] {previous, next}, color, LINE_WIDTH);
            previous = next;
        }
    }
}
