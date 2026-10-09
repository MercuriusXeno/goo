package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thumper's pulse mark: each time a thumper blob gives power, red shock
 * rings leave the blob flat against the face it sits on, expanding and
 * fading over half a second.
 * thumper-blob-pulses-periodically-then-fades
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ThumpRings {

    /** Seconds a thump's rings stand, from the pulse until they fade out. */
    static final double LIFETIME_SECONDS = 0.5;
    /** A ring's radius as it leaves the blob, in blocks. */
    static final double START_RADIUS = 0.2;
    /** A ring's radius as it fades out, in blocks. */
    static final double END_RADIUS = 1.8;
    /** The second ring trails the first by this share of the lifetime. */
    private static final double TRAIL_SHARE = 0.3;
    private static final int SEGMENTS = 32;
    private static final int RING_RGB = 0xE0301E;
    private static final float PEAK_ALPHA = 230f;
    private static final float WIDTH_SCALE = 2f;
    /** How far the rings stand off the face, so they sit on the blob rather than in the block. */
    private static final double FACE_LIFT = 0.12;
    private static final double BLOCK_CENTER = 0.5;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /** Each pulsing blob's last pulse, kept until its rings fade. */
    private static final Map<BlockPos, Thump> THUMPS = new ConcurrentHashMap<>();

    private ThumpRings() {
    }

    /**
     * One pulse a blob gave.
     *
     * @param face        the face the blob sits on
     * @param bornSeconds the real-time clock at the pulse
     */
    record Thump(Direction face, double bornSeconds) {
    }

    /**
     * Notes a blob's powered state as the renderer reads it: a blob found
     * powered with no thump standing starts one.
     *
     * @param pos     the blob's block
     * @param face    the face the blob sits on
     * @param powered whether the blob gives power now
     */
    public static void see(BlockPos pos, Direction face, boolean powered) {
        if (powered) {
            double now = nowSeconds();
            THUMPS.compute(pos.immutable(), (key, thump) -> thump == null || startsAnew(now - thump.bornSeconds())
                    ? new Thump(face, now) : thump);
        }
    }

    /**
     * Whether a powered blob starts a new thump: once the last one's rings
     * have faded, so one pulse, read on many frames, thumps once.
     *
     * @param sinceLast seconds since the blob's last thump
     * @return true when the last thump has faded
     */
    static boolean startsAnew(double sinceLast) {
        return sinceLast >= LIFETIME_SECONDS;
    }

    /** Drops every thump, as a disconnect does. */
    public static void clear() {
        THUMPS.clear();
    }

    /**
     * Draws each standing thump after the translucent blocks and drops the faded ones.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (THUMPS.isEmpty() || mc.level == null) {
            return;
        }
        double now = nowSeconds();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        THUMPS.forEach((pos, thump) -> {
            double share = (now - thump.bornSeconds()) / LIFETIME_SECONDS;
            if (share >= 1 + TRAIL_SHARE) {
                THUMPS.remove(pos, thump);
            } else {
                drawRing(lines, pos, thump.face(), share, camera, width);
                drawRing(lines, pos, thump.face(), share - TRAIL_SHARE, camera, width);
            }
        });
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    private static void drawRing(LineContext lines, BlockPos pos, Direction face, double share, Vec3 camera,
                                 float width) {
        int alpha = Math.round(PEAK_ALPHA * opacity(share));
        if (alpha <= 0) {
            return;
        }
        Vec3 normal = face.getUnitVec3();
        Vec3 center = Vec3.atCenterOf(pos).subtract(normal.scale(BLOCK_CENTER - FACE_LIFT));
        lines.emitPolyline(camera, SignalRings.ringPoints(center, normal, radiusAt(share), SEGMENTS),
                ARGB.color(alpha, RING_RGB), width);
    }

    /**
     * A ring's opacity along its life: full as it leaves the blob, fading
     * steadily to nothing; unseen before it starts.
     *
     * @param share the share of the ring's life, 0 at the pulse
     * @return the opacity, 0 to 1
     */
    static float opacity(double share) {
        return share < 0 ? 0f : (float) Math.max(0, 1 - share);
    }

    /**
     * A ring's radius along its life: small at the blob, expanding out.
     *
     * @param share the share of the ring's life, 0 at the pulse
     * @return the radius in blocks
     */
    static double radiusAt(double share) {
        return START_RADIUS + (END_RADIUS - START_RADIUS) * Math.max(0, Math.min(1, share));
    }

    private static double nowSeconds() {
        return System.nanoTime() / NANOS_PER_SECOND;
    }
}
