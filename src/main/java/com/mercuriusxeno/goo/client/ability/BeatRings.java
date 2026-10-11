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
 * Metronome's beat mark: each time the prism gives power, red shock rings
 * leave its base flat against the face it sits on, expanding and fading
 * over half a second.
 * metronome-prism-pulses-at-the-learned-rate
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BeatRings {

    /** Seconds a beat's rings stand, from the pulse until they fade out. */
    static final double LIFETIME_SECONDS = 0.5;
    /** A ring's radius as it leaves the prism, in blocks. */
    static final double START_RADIUS = 0.2;
    /** A ring's radius as it fades out, in blocks: the reach of the prism's power to the blocks beside it. */
    static final double END_RADIUS = 1.0;
    /** The second ring trails the first by this share of the lifetime. */
    private static final double TRAIL_SHARE = 0.3;
    private static final int SEGMENTS = 32;
    private static final int RING_RGB = 0xE0301E;
    private static final float PEAK_ALPHA = 230f;
    private static final float WIDTH_SCALE = 2f;
    /** How far the rings stand off the face, so they sit on the prism rather than in the block. */
    private static final double FACE_LIFT = 0.12;
    private static final double BLOCK_CENTER = 0.5;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /** Each beating prism's last pulse, kept until its rings fade. */
    private static final Map<BlockPos, Beat> BEATS = new ConcurrentHashMap<>();

    private BeatRings() {
    }

    /**
     * One beat a prism gave.
     *
     * @param face        the face the prism sits on
     * @param bornSeconds the real-time clock at the beat
     */
    record Beat(Direction face, double bornSeconds) {
    }

    /**
     * Notes a prism's powered state as the renderer reads it: a prism found
     * powered with no beat standing starts one.
     *
     * @param pos     the prism's block
     * @param face    the face the prism sits on
     * @param powered whether the prism gives power now
     */
    public static void see(BlockPos pos, Direction face, boolean powered) {
        if (powered) {
            double now = nowSeconds();
            BEATS.compute(pos.immutable(), (key, beat) -> beat == null || startsAnew(now - beat.bornSeconds())
                    ? new Beat(face, now) : beat);
        }
    }

    /**
     * Whether a powered prism starts a new beat: once the last one's rings
     * have faded, so one pulse, read on many frames, rings once.
     *
     * @param sinceLast seconds since the prism's last beat
     * @return true when the last beat has faded
     */
    static boolean startsAnew(double sinceLast) {
        return sinceLast >= LIFETIME_SECONDS;
    }

    /** Drops every beat, as a disconnect does. */
    public static void clear() {
        BEATS.clear();
    }

    /**
     * Draws each standing beat after the translucent blocks and drops the faded ones.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (BEATS.isEmpty() || mc.level == null) {
            return;
        }
        double now = nowSeconds();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        BEATS.forEach((pos, beat) -> {
            double share = (now - beat.bornSeconds()) / LIFETIME_SECONDS;
            if (share >= 1 + TRAIL_SHARE) {
                BEATS.remove(pos, beat);
            } else {
                drawRing(lines, pos, beat.face(), share, camera, width);
                drawRing(lines, pos, beat.face(), share - TRAIL_SHARE, camera, width);
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
        lines.emitPolyline(camera, SignalRings.ringPoints(center, normal, radiusAt(share), SEGMENTS, 0),
                ARGB.color(alpha, RING_RGB), width);
    }

    /**
     * A ring's opacity along its life: full as it leaves the prism, fading
     * steadily to nothing; unseen before it starts.
     *
     * @param share the share of the ring's life, 0 at the pulse
     * @return the opacity, 0 to 1
     */
    static float opacity(double share) {
        return share < 0 ? 0f : (float) Math.max(0, 1 - share);
    }

    /**
     * A ring's radius along its life: small at the prism, expanding out.
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
