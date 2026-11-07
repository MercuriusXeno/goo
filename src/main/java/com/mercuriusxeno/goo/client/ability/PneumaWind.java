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
 * Pneuma's look while held: soft motes of air, drawn as the same really soft
 * wind lines Float's air control rises in, leave a spread in front of the
 * player and are drawn into its mouth, quickening as they near it, so the
 * hold reads as a breath being taken. The motes ride along with the head.
 * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PneumaWind {

    /** Ticks a mote takes from leaving to reaching the mouth. */
    static final int LIFE_TICKS = 16;
    /** Ticks between motes leaving while held. */
    static final int EVERY = 2;
    /** How far ahead of the eye a mote leaves, in blocks. */
    static final double REACH = 1.6;
    /** The radius of the disc across the look a mote leaves from, in blocks. */
    static final double SPREAD = 0.7;
    /** Where the mouth stands: a little ahead of the eye and below it, in blocks. */
    static final double MOUTH_AHEAD = 0.25;
    static final double MOUTH_DROP = 0.2;
    /** Ticks since the last held tick that still count as held, so a late sync leaves no gap. */
    static final int HELD_GRACE = 2;
    /** Ticks of the path a mote's tail trails behind its head. */
    private static final double TAIL_TICKS = 5;
    private static final int TAIL_SAMPLES = 8;
    /** The share of its life a mote fades in over, and out over as it reaches the mouth. */
    private static final double FADE_IN_SHARE = 0.25;
    private static final double FADE_OUT_SHARE = 0.15;
    private static final int MAX_ALPHA = 120;
    private static final int WHITE = 0xF4F8FF;
    private static final float LINE_WIDTH = 2.5f;
    private static final double TWO_PI = 2 * Math.PI;

    /**
     * One mote of air being breathed in, its path fixed relative to the eye when it left.
     *
     * @param start     where it leaves, relative to the eye
     * @param mouth     where it ends, relative to the eye
     * @param startTick the game time it left
     */
    record Mote(Vec3 start, Vec3 mouth, long startTick) {
    }

    private static final List<Mote> LIVE = new ArrayList<>();
    private static long lastSpawnTick = -1;

    private PneumaWind() {
    }

    /**
     * Where a mote's head stands a number of ticks after it left, relative to
     * the eye: drawn from its start toward the mouth, slow at first and
     * quickening, as a breath pulls air in.
     *
     * @param mote the mote
     * @param age  ticks since it left
     * @return the head's position relative to the eye
     */
    static Vec3 motePoint(Mote mote, double age) {
        double life = Math.clamp(age / LIFE_TICKS, 0, 1);
        return mote.start().lerp(mote.mouth(), life * life);
    }

    /**
     * A mote's opacity over its life: fading in as it leaves, out as it reaches the mouth.
     *
     * @param life the share of its life lived, 0 to 1
     * @return the opacity, 0 to 1
     */
    static double opacity(double life) {
        return Math.clamp(Math.min(life / FADE_IN_SHARE, (1 - life) / FADE_OUT_SHARE), 0, 1);
    }

    /**
     * Where a mote leaves, relative to the eye: ahead along the look, offset
     * across it within the spread disc.
     *
     * @param look   the player's look, a unit vector
     * @param angle  the angle about the look, in radians
     * @param radius the share of the spread out from the look, 0 to 1
     * @return the start relative to the eye
     */
    static Vec3 startAhead(Vec3 look, double angle, double radius) {
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        Vec3 across = side.lengthSqr() > 0 ? side.normalize() : new Vec3(1, 0, 0);
        Vec3 up = across.cross(look).normalize();
        double out = radius * SPREAD;
        return look.scale(REACH).add(across.scale(Math.cos(angle) * out)).add(up.scale(Math.sin(angle) * out));
    }

    /**
     * Where the mouth stands relative to the eye for a look.
     *
     * @param look the player's look, a unit vector
     * @return the mouth relative to the eye
     */
    static Vec3 mouth(Vec3 look) {
        return look.scale(MOUTH_AHEAD).add(0, -MOUTH_DROP, 0);
    }

    /**
     * Spawns this tick's motes while the local player holds Pneuma, drops
     * spent ones and draws the rest.
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
        if (player.getData(GooAttachments.BREATH).heldWithin(now, HELD_GRACE)) {
            spawn(player, now);
        }
        LIVE.removeIf(mote -> now - mote.startTick() >= LIFE_TICKS);
        if (!LIVE.isEmpty()) {
            drawMotes(event, mc, player, now);
        }
    }

    private static void spawn(LocalPlayer player, long now) {
        if (now == lastSpawnTick || now % EVERY != 0) {
            return;
        }
        lastSpawnTick = now;
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        LIVE.add(new Mote(startAhead(look, random.nextDouble() * TWO_PI, Math.sqrt(random.nextDouble())),
                mouth(look), now));
    }

    private static void drawMotes(RenderLevelStageEvent.AfterTranslucentBlocks event, Minecraft mc,
                                  LocalPlayer player, long now) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(type));
        for (Mote mote : LIVE) {
            drawMote(lines, camera, eye, mote, now + partialTick - mote.startTick());
        }
        buffers.endBatch(type);
    }

    private static void drawMote(LineContext lines, Vec3 camera, Vec3 eye, Mote mote, double age) {
        int alpha = (int) Math.round(MAX_ALPHA * opacity(age / LIFE_TICKS));
        if (alpha <= 0) {
            return;
        }
        Vec3 previous = eye.add(motePoint(mote, age));
        for (int sample = 1; sample <= TAIL_SAMPLES; sample++) {
            double tail = (double) sample / TAIL_SAMPLES;
            Vec3 next = eye.add(motePoint(mote, age - tail * TAIL_TICKS));
            int color = ARGB.color((int) Math.round(alpha * (1 - tail)), WHITE);
            lines.emitPolyline(camera, new Vec3[] {previous, next}, color, LINE_WIDTH);
            previous = next;
        }
    }
}
