package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Glitter's sphere on the caster's client: while the channel is held its
 * front grows from where the hold began as a loose band of glitter, sparse
 * tiny stars scattered at random over the sphere and a little either side
 * of it, each mostly dark and flashing briefly, its prismatic hue drifting;
 * once the hold's ticks stop arriving the front stops where it stands and
 * the band fades (operator ruling 2026-10-10: holding it down makes it go
 * farther); a faint ghost skin marks the front, and a
 * thin pale line runs where it cuts the floors and walls, smooth rather
 * than Scry's lit faces (operator ruling 2026-10-10: chaotic, sparse, varied in
 * distance, small, dim and prismatic, never a grid).
 * decision glitter-sphere-icons-gem-ore-groups
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GlitterShell {

    /** Glints scattered over one ping's band, sparse so the band reads as glitter rather than a mesh. */
    static final int GLINTS = 260;
    /** Ticks the band takes to fade once the hold ends. */
    static final float FADE_TICKS = 8f;
    /** Ticks after the last held tick arrived that the hold still counts as held, through network jitter. */
    static final float HELD_GAP_TICKS = 3f;
    /** How far a glint lies off the front, as a share of the radius, either way. */
    static final double DEPTH_SCATTER = 0.18;
    /** Every glint flashes this bright at most, dim so the band glitters rather than glares. */
    static final float BRIGHTEST = 0.3f;
    /** A ping's band centers at the caster's body, this far above where the caster stood. */
    private static final double BODY_CENTER = 0.9;
    /** A glint's arm length in blocks at the center, before it grows with the front. */
    private static final float GLINT_BASE = 0.03f;
    /** Blocks a glint's arm grows per block of radius, so far glints still read. */
    private static final float GLINT_PER_BLOCK = 0.012f;
    /** A glint arm's width as a share of its length. */
    private static final float ARM_WIDTH = 0.16f;
    /** The diagonal arms' length as a share of the upright arms'. */
    private static final float DIAGONAL_ARMS = 0.45f;
    /** The share of glints that flash diamond-white rather than a hue. */
    private static final double WHITE_SHARE = 0.15;
    /** The hued glints' saturation: prismatic fire, not pastel. */
    private static final float PRISM_SATURATION = 0.75f;
    /** The hue a glint drifts through each tick, so its color runs the prism. */
    private static final double HUE_DRIFT = 0.02;
    /** The slowest and the span of a glint's twinkle, in radians a tick. */
    private static final double TWINKLE_SLOWEST = 0.3;
    private static final double TWINKLE_SPAN = 0.8;
    /** The power a twinkle is raised to: high, so a glint is mostly dark and flashes briefly. */
    private static final int FLASH_SHARPNESS = 6;
    private static final int OPAQUE = 255;
    private static final float HALF = 0.5f;
    private static final double BOTH_WAYS = 2;
    /** A diamond-white glint's color, without its alpha. */
    private static final int WHITE_RGB = 0xFFFFFF;
    /** The low bytes of a packed color, its red, green and blue. */
    private static final int RGB_MASK = 0xFFFFFF;
    /** Mixes the origin into the ping's seed. */
    private static final long SEED_MIX = 31L;
    /** The ghost edge's alpha: faint, a see-through skin marking how far the front has gone. */
    private static final int EDGE_ALPHA = 22;
    /** The ghost edge's pale crystal white. */
    private static final int EDGE_RGB = 0xD8E8FF;
    /** The surface line's alpha at full strength. */
    private static final float LINE_ALPHA = 0.55f;
    /** The surface line's width, in blocks. */
    private static final double LINE_WIDTH = 0.07;
    /** How far the surface line stands off its face, so it never sinks into it. */
    private static final double LINE_LIFT = 0.01;
    /** The surface line's saturation: a pale prismatic sheen. */
    private static final float LINE_SATURATION = 0.4f;
    /** The hue the surface line shifts through per block along the ground. */
    private static final double LINE_HUE_PER_BLOCK = 0.03;
    /** How far past the front a block's center may lie and still hold a face the front cuts. */
    private static final double FACE_REACH = 0.9;
    /** The most faces one ping's front keeps to cut at once. */
    private static final int MOST_FACES = 6000;

    private static final List<Ping> PINGS = new ArrayList<>();

    private GlitterShell() {
    }

    /**
     * One glint of a ping's band.
     *
     * @param direction its unit direction from the center
     * @param depth     its distance against the front's, near 1
     * @param phase     its twinkle's phase, in radians
     * @param speed     its twinkle's speed, in radians a tick
     * @param hue       its hue at the ping's start, 0 to 1
     * @param white     whether it flashes diamond-white rather than a hue
     */
    record Glint(Vec3 direction, double depth, double phase, double speed, float hue, boolean white) {
    }

    /**
     * One hold's band, with the block faces near its front that the front
     * may cut, found as the front reaches them, and the front's radius as
     * the last held tick reported it.
     */
    private static final class Ping {
        private final Vec3 origin;
        private final Vec3 center;
        private final double growth;
        private final int radius;
        private final List<Glint> glints;
        private final List<Face> faces;
        private double scanned;
        private double front;
        private long heardAt;

        Ping(OreRevealPayload payload, long now, List<Glint> glints) {
            this.origin = payload.origin();
            this.center = payload.origin().add(0, BODY_CENTER, 0);
            this.growth = payload.growth();
            this.radius = payload.radius();
            this.glints = glints;
            this.faces = new ArrayList<>();
            hear(payload, now);
        }

        void hear(OreRevealPayload payload, long now) {
            front = payload.front();
            heardAt = now;
        }

        boolean continues(OreRevealPayload payload, long now) {
            return now - heardAt <= HELD_GAP_TICKS && origin.equals(payload.origin());
        }

        double frontNow(double now) {
            return frontAt(front, now - heardAt, growth, radius);
        }

        float strengthNow(double now) {
            return strengthAt(now - heardAt);
        }

        Vec3 center() {
            return center;
        }

        List<Glint> glints() {
            return glints;
        }

        List<Face> faces() {
            return faces;
        }

        double scanned() {
            return scanned;
        }

        void scannedTo(double reach) {
            scanned = reach;
        }
    }

    /**
     * A block face open to air near the front.
     *
     * @param pos      the block
     * @param side     the face's side
     * @param farthest the front's radius past which it has left the face behind
     */
    private record Face(BlockPos pos, Direction side, double farthest) {
    }

    /**
     * Follows a held tick: moves the front of the hold's band out, or
     * starts a band for a new hold.
     *
     * @param payload the held tick
     * @param now     the game time it arrived at
     */
    public static void follow(OreRevealPayload payload, long now) {
        for (Ping ping : PINGS) {
            if (ping.continues(payload, now)) {
                ping.hear(payload, now);
                return;
            }
        }
        long seed = Double.doubleToLongBits(payload.origin().x) * SEED_MIX + now;
        PINGS.add(new Ping(payload, now, glints(GLINTS, seed)));
    }

    /** Drops every band, as a disconnect does. */
    public static void clear() {
        PINGS.clear();
    }

    /**
     * A ping's glints, scattered at random: each in a random direction, a
     * random way off the front, with its own twinkle and hue.
     *
     * @param count the glints
     * @param seed  the scatter's seed
     * @return the glints
     */
    static List<Glint> glints(int count, long seed) {
        RandomSource random = RandomSource.create(seed);
        List<Glint> glints = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double height = random.nextDouble() * BOTH_WAYS - 1;
            double ring = Math.sqrt(Math.max(0, 1 - height * height));
            double longitude = random.nextDouble() * Math.TAU;
            Vec3 direction = new Vec3(ring * Math.cos(longitude), height, ring * Math.sin(longitude));
            double depth = 1 + (random.nextDouble() * BOTH_WAYS - 1) * DEPTH_SCATTER;
            glints.add(new Glint(direction, depth, random.nextDouble() * Math.TAU,
                    TWINKLE_SLOWEST + random.nextDouble() * TWINKLE_SPAN, random.nextFloat(),
                    random.nextDouble() < WHITE_SHARE));
        }
        return glints;
    }

    /**
     * The front's radius a while after the last held tick reported it:
     * carried on through the tick to come, so it grows smoothly while held,
     * and stopped there when no further tick comes.
     *
     * @param front  the radius the last held tick reported
     * @param since  ticks since that tick arrived
     * @param growth blocks the front grows each held tick
     * @param radius the radius the front reaches at most
     * @return the radius, held at the reach once reached
     */
    static double frontAt(double front, double since, double growth, int radius) {
        return Math.min(radius, front + Math.clamp(since, 0, 1) * growth);
    }

    /**
     * How strongly the band shows a while after the last held tick arrived:
     * whole while the hold lasts, fading to nothing once the ticks stop.
     *
     * @param since ticks since the last held tick arrived
     * @return the strength in [0, 1]
     */
    static float strengthAt(double since) {
        return (float) Math.clamp(1 - (since - HELD_GAP_TICKS) / FADE_TICKS, 0, 1);
    }

    /**
     * How brightly a glint flashes at a moment: dark through most of its
     * twinkle, flashing sharply at its peak, never past the brightest.
     *
     * @param glint the glint
     * @param now   the game time with its partial tick
     * @return its flash, 0 to the brightest
     */
    static float flashAt(Glint glint, double now) {
        double rising = Math.max(0, Math.sin(glint.phase() + now * glint.speed()));
        return (float) Math.pow(rising, FLASH_SHARPNESS) * BRIGHTEST;
    }

    /**
     * A glint's color at a moment: diamond-white, or its hue drifted through
     * the prism by the moment, at the alpha given.
     *
     * @param glint the glint
     * @param now   the game time with its partial tick
     * @param alpha its alpha in [0, 1]
     * @return the packed ARGB color
     */
    static int glintColor(Glint glint, double now, float alpha) {
        int rgb = glint.white() ? WHITE_RGB
                : Color.HSBtoRGB((float) ((glint.hue() + now * HUE_DRIFT) % 1), PRISM_SATURATION, 1f) & RGB_MASK;
        return ARGB.color(Math.round(OPAQUE * Math.clamp(alpha, 0f, 1f)), rgb);
    }

    /**
     * Draws every ping's band once the world has drawn, and drops the
     * bands that have faded.
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
        PINGS.removeIf(ping -> ping.strengthNow(now) <= 0f);
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE);
        for (Ping ping : PINGS) {
            findFaces(mc.level, ping, ping.frontNow(now));
            drawPing(pose, consumer, camera, ping, now);
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Finds the faces open to air that the front has come within reach of,
     * and drops the faces it has left behind.
     *
     * @param level  the client level
     * @param ping   the ping
     * @param radius the front's radius now
     */
    private static void findFaces(ClientLevel level, Ping ping, double radius) {
        ping.faces().removeIf(face -> face.farthest() < radius);
        double reach = radius + FACE_REACH;
        if (reach <= ping.scanned()) {
            return;
        }
        for (BlockPos pos : ScryReveal.shell(ping.center(), ping.scanned(), reach)) {
            for (Direction side : ScryReveal.exposedFaces(at -> level.getBlockState(at).isAir(), pos)) {
                if (ping.faces().size() < MOST_FACES) {
                    ping.faces().add(new Face(pos, side, SurfaceArcs.farthest(ping.center(), pos, side)));
                }
            }
        }
        ping.scannedTo(reach);
    }

    private static void drawPing(PoseStack.Pose pose, VertexConsumer consumer, Camera camera, Ping ping, double now) {
        float strength = ping.strengthNow(now);
        double radius = ping.frontNow(now);
        if (strength <= 0f || radius <= 0) {
            return;
        }
        Vec3 center = ping.center().subtract(camera.position());
        ColorSphere.emit(pose, consumer, center, (float) radius,
                ARGB.color(Math.round(EDGE_ALPHA * strength), EDGE_RGB));
        drawSurfaceLine(pose, consumer, camera.position(), ping, radius, strength, now);
        float arm = GLINT_BASE + (float) radius * GLINT_PER_BLOCK;
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        for (Glint glint : ping.glints()) {
            float flash = flashAt(glint, now);
            if (flash > 0f) {
                Vec3 at = center.add(glint.direction().scale(radius * glint.depth()));
                emitStar(pose, consumer, at, up, left, arm * (HALF + flash / BRIGHTEST * HALF),
                        glintColor(glint, now, flash * strength));
            }
        }
    }

    /**
     * Draws the front's line over every face it cuts: a thin ribbon lying on
     * the face along the arc the sphere cuts there, its pale hue running
     * along the ground.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer, position and color
     * @param camera   the camera's position
     * @param ping     the ping
     * @param radius   the front's radius now
     * @param strength how strongly the band shows
     * @param now      the game time with its partial tick
     */
    private static void drawSurfaceLine(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, Ping ping,
                                        double radius, float strength, double now) {
        for (Face face : ping.faces()) {
            Vec3 normal = Vec3.atLowerCornerOf(face.side().getUnitVec3i());
            for (List<Vec3> arc : SurfaceArcs.arcs(ping.center(), radius, face.pos(), face.side())) {
                for (int index = 1; index < arc.size(); index++) {
                    Vec3 from = arc.get(index - 1);
                    Vec3 to = arc.get(index);
                    float hue = (float) ((from.x + from.z) * LINE_HUE_PER_BLOCK + now * HUE_DRIFT) % 1;
                    int color = ARGB.color(Math.round(OPAQUE * LINE_ALPHA * strength),
                            Color.HSBtoRGB(hue < 0 ? hue + 1 : hue, LINE_SATURATION, 1f) & RGB_MASK);
                    emitRibbon(pose, consumer, from.subtract(camera), to.subtract(camera), normal, color);
                }
            }
        }
    }

    private static void emitRibbon(PoseStack.Pose pose, VertexConsumer consumer, Vec3 from, Vec3 to, Vec3 normal,
                                   int color) {
        Vec3 lift = normal.scale(LINE_LIFT);
        Vec3 across = to.subtract(from).cross(normal).normalize().scale(LINE_WIDTH * HALF);
        vertex(pose, consumer, from.add(lift).add(across), color);
        vertex(pose, consumer, to.add(lift).add(across), color);
        vertex(pose, consumer, to.add(lift).subtract(across), color);
        vertex(pose, consumer, from.add(lift).subtract(across), color);
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
