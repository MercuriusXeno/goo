package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zap's strike: a pulse beam draws no traveling beam but a small red
 * lightning bolt from the glove hand to the strike point, its kinks jumping
 * every few frames as it crackles and the whole bolt fading over a very
 * quick animation.
 * zap-ticks-the-device-and-stuns
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ZapBolts {

    /** Seconds a bolt stands from the strike until it has faded out. */
    static final double LIFETIME_SECONDS = 0.3;
    /** Seconds each crackle holds its kinks before they jump. */
    static final double CRACKLE_SECONDS = 0.05;
    /** The straight runs a bolt is drawn with between its kinks. */
    static final int SEGMENTS = 9;
    /** How far a kink strays from the straight line at most, in blocks. */
    static final double JITTER = 0.22;
    /** The kink a fork leaves the bolt at, counted from the hand. */
    private static final int FORK_AT = 5;
    /** A fork's length against the remaining bolt's. */
    private static final double FORK_SHARE = 0.35;
    private static final int FORK_SEGMENTS = 3;
    /** How far a fork swings out to the side of the bolt, in blocks. */
    private static final double FORK_SWING = 0.45;
    private static final int GLOW_RGB = 0xE0301E;
    private static final int CORE_RGB = 0xFFB0A0;
    private static final float PEAK_ALPHA = 255f;
    private static final float GLOW_WIDTH_SCALE = 3f;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final long FORK_SEED_SALT = 0x5EEDL;

    private static final List<Bolt> BOLTS = new CopyOnWriteArrayList<>();

    private ZapBolts() {
    }

    /**
     * One strike standing on screen.
     *
     * @param hand        where the bolt leaves, the glove hand
     * @param strike      where the bolt lands
     * @param bornSeconds the real-time clock at the strike
     * @param seed        the bolt's own seed, so two bolts crackle apart
     */
    record Bolt(Vec3 hand, Vec3 strike, double bornSeconds, long seed) {
    }

    /**
     * Whether a flight strikes as lightning rather than flying: a pulse beam.
     *
     * @param gooType  the thrown goo type
     * @param delivery the thrown ability's delivery
     * @return true for a pulse beam
     */
    public static boolean strikesAsLightning(ResourceKey<GooTypeDefinition> gooType, Delivery delivery) {
        return gooType == GooTypes.PULSE && delivery.fliesStraight();
    }

    /**
     * Adds a bolt from the hand to the strike point, standing from now.
     *
     * @param hand   where the bolt leaves
     * @param strike where the bolt lands
     */
    public static void strike(Vec3 hand, Vec3 strike) {
        double now = nowSeconds();
        BOLTS.add(new Bolt(hand, strike, now, Double.doubleToLongBits(now)));
    }

    /** Drops every bolt, as a disconnect does. */
    public static void clear() {
        BOLTS.clear();
    }

    /**
     * Draws every standing bolt after the translucent blocks and drops the faded ones.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (BOLTS.isEmpty() || mc.level == null) {
            return;
        }
        double now = nowSeconds();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth();
        for (Bolt bolt : BOLTS) {
            double age = now - bolt.bornSeconds();
            if (age >= LIFETIME_SECONDS) {
                BOLTS.remove(bolt);
            } else {
                drawBolt(lines, bolt, age, camera, width);
            }
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    private static void drawBolt(LineContext lines, Bolt bolt, double age, Vec3 camera, float width) {
        float fade = opacity(age);
        Random crackle = new Random(bolt.seed() + crackleFrame(age));
        Vec3[] main = boltPoints(bolt.hand(), bolt.strike(), SEGMENTS, JITTER, crackle);
        Vec3 forkEnd = main[FORK_AT].add(bolt.strike().subtract(main[FORK_AT]).scale(FORK_SHARE))
                .add(StreamCone.side(bolt.strike().subtract(bolt.hand())).scale(FORK_SWING));
        Vec3[] fork = boltPoints(main[FORK_AT], forkEnd, FORK_SEGMENTS, JITTER,
                new Random((bolt.seed() ^ FORK_SEED_SALT) + crackleFrame(age)));
        int glow = ARGB.color(Math.round(PEAK_ALPHA * fade), GLOW_RGB);
        int core = ARGB.color(Math.round(PEAK_ALPHA * fade), CORE_RGB);
        lines.emitPolyline(camera, main, glow, width * GLOW_WIDTH_SCALE);
        lines.emitPolyline(camera, main, core, width);
        lines.emitPolyline(camera, fork, glow, width);
    }

    /**
     * Which crackle a bolt shows at an age: the kinks hold for a crackle,
     * then jump.
     *
     * @param ageSeconds seconds since the strike
     * @return the crackle's index, from 0 at the strike
     */
    static long crackleFrame(double ageSeconds) {
        return (long) Math.floor(ageSeconds / CRACKLE_SECONDS);
    }

    /**
     * A bolt's opacity at an age: full at the strike, fading steadily, gone
     * at the end of its lifetime.
     *
     * @param ageSeconds seconds since the strike
     * @return the opacity, 0 to 1
     */
    static float opacity(double ageSeconds) {
        return (float) Math.max(0, Math.min(1, 1 - ageSeconds / LIFETIME_SECONDS));
    }

    /**
     * The kinked points of a bolt: pinned at both ends, each kink between
     * strayed sideways from the straight line, most in the middle and least
     * near the ends.
     *
     * @param from     where the bolt leaves
     * @param to       where the bolt lands
     * @param segments the straight runs between kinks
     * @param jitter   how far a kink strays at most, in blocks
     * @param random   the crackle's random source
     * @return the points, from first to last
     */
    static Vec3[] boltPoints(Vec3 from, Vec3 to, int segments, double jitter, Random random) {
        Vec3 axis = to.subtract(from);
        Vec3 side = StreamCone.side(axis);
        Vec3 lift = side.cross(axis.normalize());
        Vec3[] points = new Vec3[segments + 1];
        points[0] = from;
        for (int i = 1; i < segments; i++) {
            double share = (double) i / segments;
            double reach = jitter * Math.sin(Math.PI * share);
            double sideways = random.nextDouble(-reach, reach);
            double upward = random.nextDouble(-reach, reach);
            points[i] = from.add(axis.scale(share)).add(side.scale(sideways)).add(lift.scale(upward));
        }
        points[segments] = to;
        return points;
    }

    private static double nowSeconds() {
        return System.nanoTime() / NANOS_PER_SECOND;
    }
}
