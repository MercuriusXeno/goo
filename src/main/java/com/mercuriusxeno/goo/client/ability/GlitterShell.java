package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3fc;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Glitter's sphere on the caster's client: each ping's front grows from
 * where the caster stood as a thin shell of twinkling glints, diamond-white
 * and soft rainbow four-point stars scattered over the sphere, fading once
 * the front reaches its radius. It lights no block faces, so it reads apart
 * from Scry's sweep.
 * decision glitter-sphere-icons-gem-ore-groups
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GlitterShell {

    /** Glints scattered over one ping's shell. */
    static final int GLINTS = 720;
    /** Ticks the shell takes to fade once its front reaches its radius. */
    static final float FADE_TICKS = 8f;
    /** A ping's shell centers at the caster's body, this far above where the caster stood. */
    private static final double BODY_CENTER = 0.9;
    /** A glint's arm length in blocks at the center, before it grows with the front. */
    private static final float GLINT_BASE = 0.08f;
    /** Blocks a glint's arm grows per block of radius, so far glints still read. */
    private static final float GLINT_PER_BLOCK = 0.035f;
    /** A glint arm's width as a share of its length. */
    private static final float ARM_WIDTH = 0.16f;
    /** The diagonal arms' length as a share of the upright arms'. */
    private static final float DIAGONAL_ARMS = 0.45f;
    /** Every this many glints one is diamond-white rather than a rainbow hue. */
    private static final int WHITE_EVERY = 3;
    /** The rainbow glints' saturation, soft so they read as gem fire rather than paint. */
    private static final float RAINBOW_SATURATION = 0.45f;
    /** The golden ratio's fraction, spreading each glint's hue and longitude apart from its neighbour's. */
    private static final double GOLDEN = 0.6180339887;
    /** The slowest and the span of a glint's twinkle, in radians a tick. */
    private static final double TWINKLE_SLOWEST = 0.25;
    private static final double TWINKLE_SPAN = 0.5;
    /** How far a glint's direction is jittered off its even place, as a share of the spacing. */
    private static final double JITTER = 0.6;
    private static final int OPAQUE = 255;
    private static final float HALF = 0.5f;
    /** A diamond-white glint's color, without its alpha. */
    private static final int WHITE_RGB = 0xFFFFFF;
    /** The low bytes of a packed color, its red, green and blue. */
    private static final int RGB_MASK = 0xFFFFFF;
    /** Each glint holds two twinkle values, its phase then its speed. */
    private static final int PER_GLINT = 2;
    /** Mixes the origin into the ping's seed. */
    private static final long SEED_MIX = 31L;

    private static final List<Ping> PINGS = new ArrayList<>();

    private GlitterShell() {
    }

    /**
     * One ping's shell.
     *
     * @param center     the shell's center
     * @param growth     blocks the front grows each tick
     * @param radius     the blocks the front reaches
     * @param startedAt  the game time the ping began
     * @param directions each glint's unit direction from the center
     * @param phases     each glint's twinkle phase and speed, two per glint, as {@link #twinkles} lays them
     */
    private record Ping(Vec3 center, double growth, int radius, long startedAt, List<Vec3> directions,
                        double[] phases) {
    }

    /**
     * Starts a ping's shell.
     *
     * @param payload the ping
     * @param now     the game time it arrived at
     */
    public static void start(OreRevealPayload payload, long now) {
        long seed = Double.doubleToLongBits(payload.origin().x) * SEED_MIX + now;
        PINGS.add(new Ping(payload.origin().add(0, BODY_CENTER, 0), payload.growth(), payload.radius(), now,
                glintDirections(GLINTS, seed), twinkles(GLINTS, seed + 1)));
    }

    /** Drops every shell, as a disconnect does. */
    public static void clear() {
        PINGS.clear();
    }

    /**
     * Each glint's twinkle: a phase, then a speed in radians a tick.
     *
     * @param count the glints
     * @param seed  the twinkles' seed
     * @return two values per glint, phase then speed
     */
    static double[] twinkles(int count, long seed) {
        RandomSource random = RandomSource.create(seed);
        double[] phases = new double[count * PER_GLINT];
        for (int index = 0; index < count; index++) {
            phases[index * PER_GLINT] = random.nextDouble() * Math.TAU;
            phases[index * PER_GLINT + 1] = TWINKLE_SLOWEST + random.nextDouble() * TWINKLE_SPAN;
        }
        return phases;
    }

    /**
     * Unit directions spread evenly over the sphere, each jittered off its
     * even place so the glints read scattered rather than in rows.
     *
     * @param count the glints
     * @param seed  the jitter's seed
     * @return a unit direction per glint
     */
    static List<Vec3> glintDirections(int count, long seed) {
        RandomSource random = RandomSource.create(seed);
        List<Vec3> directions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double height = 1 - (index + HALF + (random.nextDouble() - HALF) * JITTER) * PER_GLINT / count;
            double ring = Math.sqrt(Math.max(0, 1 - height * height));
            double longitude = Math.TAU * (index * GOLDEN + (random.nextDouble() - HALF) * JITTER / count);
            directions.add(new Vec3(ring * Math.cos(longitude), height, ring * Math.sin(longitude)).normalize());
        }
        return directions;
    }

    /**
     * The front's radius a while into the ping.
     *
     * @param age    ticks since the ping began
     * @param growth blocks the front grows each tick
     * @param radius the blocks the front reaches
     * @return the radius, held at the reach once reached
     */
    static double frontRadius(double age, double growth, int radius) {
        return Math.clamp(age * growth, 0, radius);
    }

    /**
     * How strongly the shell shows a while into the ping: whole while the
     * front grows, fading to nothing over the ticks after it reaches its radius.
     *
     * @param age    ticks since the ping began
     * @param growth blocks the front grows each tick
     * @param radius the blocks the front reaches
     * @return the strength in [0, 1]
     */
    static float strength(double age, double growth, int radius) {
        double reached = radius / Math.max(growth, Double.MIN_VALUE);
        return (float) Math.clamp(1 - (age - reached) / FADE_TICKS, 0, 1);
    }

    /**
     * A glint's color: every third diamond-white, the rest soft rainbow
     * hues spread apart, at the alpha given.
     *
     * @param index the glint
     * @param alpha its alpha in [0, 1]
     * @return the packed ARGB color
     */
    static int glintColor(int index, float alpha) {
        int rgb = index % WHITE_EVERY == 0 ? WHITE_RGB
                : Color.HSBtoRGB((float) (index * GOLDEN % 1), RAINBOW_SATURATION, 1f) & RGB_MASK;
        return ARGB.color(Math.round(OPAQUE * Math.clamp(alpha, 0f, 1f)), rgb);
    }

    /**
     * Draws every ping's shell once the world has drawn, and drops the
     * shells that have faded.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || PINGS.isEmpty()) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PINGS.removeIf(ping -> strength(now - ping.startedAt(), ping.growth(), ping.radius()) <= 0f);
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE);
        for (Ping ping : PINGS) {
            drawPing(pose, consumer, camera, ping, now);
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    private static void drawPing(PoseStack.Pose pose, VertexConsumer consumer, Camera camera, Ping ping, double now) {
        double age = now - ping.startedAt();
        float strength = strength(age, ping.growth(), ping.radius());
        double radius = frontRadius(age, ping.growth(), ping.radius());
        if (strength <= 0f || radius <= 0) {
            return;
        }
        Vec3 center = ping.center().subtract(camera.position());
        float arm = GLINT_BASE + (float) radius * GLINT_PER_BLOCK;
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        for (int index = 0; index < ping.directions().size(); index++) {
            double twinkle = Math.sin(ping.phases()[index * PER_GLINT] + now * ping.phases()[index * PER_GLINT + 1]);
            float alpha = (float) (twinkle * twinkle) * strength;
            Vec3 at = center.add(ping.directions().get(index).scale(radius));
            emitStar(pose, consumer, at, up, left, arm * (HALF + (float) Math.abs(twinkle) * HALF),
                    glintColor(index, alpha));
        }
    }

    /**
     * Emits a four-point star facing the camera: a long upright cross and a
     * shorter diagonal one.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer, position and color
     * @param at       the star's center, camera-relative
     * @param up       the camera's up
     * @param left     the camera's left
     * @param arm      the upright arms' length in blocks
     * @param color    the packed ARGB color
     */
    private static void emitStar(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at, Vector3fc up,
                                 Vector3fc left, float arm, int color) {
        Vec3 upright = new Vec3(up.x(), up.y(), up.z());
        Vec3 across = new Vec3(left.x(), left.y(), left.z());
        emitArm(pose, consumer, at, upright, across, arm, color);
        emitArm(pose, consumer, at, across, upright, arm, color);
        Vec3 rising = upright.add(across).normalize();
        Vec3 falling = upright.subtract(across).normalize();
        emitArm(pose, consumer, at, rising, falling, arm * DIAGONAL_ARMS, color);
        emitArm(pose, consumer, at, falling, rising, arm * DIAGONAL_ARMS, color);
    }

    private static void emitArm(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at, Vec3 along, Vec3 side,
                                float arm, int color) {
        Vec3 tip = along.scale(arm);
        Vec3 width = side.scale(arm * ARM_WIDTH);
        vertex(pose, consumer, at.add(tip), color);
        vertex(pose, consumer, at.add(width), color);
        vertex(pose, consumer, at.subtract(tip), color);
        vertex(pose, consumer, at.subtract(width), color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at, int color) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color);
    }
}
