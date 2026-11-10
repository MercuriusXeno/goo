package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Reap's slashes on the client, the Crissaegrim's from Symphony of the Night:
 * each a fixed leaf crescent drawn from the very slash the server cuts with,
 * showing only the stretch of its arc between the wipe and the sweep, the
 * sweeping head glowing pale and the stretch behind it fading to Leaf's
 * green, as the game's palette cycling lights its slash sprite.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReapSlashBlades {

    /** The quads along a whole arc, tip to tip. */
    private static final int SEGMENTS = 24;
    /** The pale green the sweeping head glows. */
    private static final int HEAD_RGB = 0xF0FFD8;
    private static final int OPAQUE = 0xFF;
    /** The share of its brightness the stretch farthest behind the head keeps. */
    private static final float TRAIL_SHARE = 0.35f;
    /** The inner edge's share of the outer edge's brightness, so the crescent glows along its rim. */
    private static final float INNER_SHARE = 0.4f;
    private static final double INNER = 0;
    private static final double OUTER = 1;
    /** The shortest stretch of arc the glow is spread across, so a stretch of none divides by nothing. */
    private static final double SHORTEST_STRETCH = 1e-6;

    private static final List<Shown> LIVE = new ArrayList<>();

    private ReapSlashBlades() {
    }

    /**
     * One slash showing.
     *
     * @param slash      the slash
     * @param launchTick the game tick it appeared
     */
    private record Shown(ReapSlash slash, long launchTick) {

        boolean overAt(long tick) {
            return tick - launchTick > slash.lifeTicks();
        }
    }

    /**
     * Starts a slash on the client thread.
     *
     * @param payload the slash
     * @param context the network context
     */
    public static void handle(ReapSlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LIVE.add(new Shown(payload.slash(), mc.level.getGameTime()));
            }
        });
    }

    /**
     * Draws every live slash after translucent blocks, dropping each that has wiped away.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIVE.clear();
            return;
        }
        long tick = mc.level.getGameTime();
        LIVE.removeIf(shown -> shown.overAt(tick));
        if (LIVE.isEmpty()) {
            return;
        }
        double time = tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE));
        int leaf = ClientGooTypes.color(GooTypes.LEAF);
        for (Shown shown : LIVE) {
            drawSlash(quads, shown.slash(), time - shown.launchTick(), camera, leaf);
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Draws the stretch of a slash's arc between its wipe and its sweep.
     *
     * @param quads  the context the quads emit through
     * @param slash  the slash
     * @param age    ticks since it appeared, with the partial tick
     * @param camera the camera's position
     * @param leaf   Leaf's green
     */
    private static void drawSlash(FlatQuadContext quads, ReapSlash slash, double age, Vec3 camera, int leaf) {
        double head = slash.headAt(age);
        double tail = slash.tailAt(age);
        if (head <= tail) {
            return;
        }
        int first = Mth.floor(tail * SEGMENTS);
        int last = Mth.ceil(head * SEGMENTS);
        for (int segment = first; segment < last; segment++) {
            double from = Math.max(tail, (double) segment / SEGMENTS);
            double to = Math.min(head, (double) (segment + 1) / SEGMENTS);
            emitSegment(quads, slash, from, to, camera, colorAt(from, head, tail, leaf), colorAt(to, head, tail, leaf));
        }
    }

    /**
     * The glow of the arc at a point: pale at the sweeping head, fading to
     * Leaf's green toward the wipe.
     *
     * @param along how far along the arc, 0 at the first tip and 1 at the other
     * @param head  how far the sweep has reached
     * @param tail  how far the wipe has reached
     * @param leaf  Leaf's green
     * @return the color
     */
    private static int colorAt(double along, double head, double tail, int leaf) {
        float nearHead = (float) Math.clamp((along - tail) / Math.max(SHORTEST_STRETCH, head - tail), 0, 1);
        int rgb = ARGB.srgbLerp(nearHead, leaf, HEAD_RGB);
        return ARGB.color(Mth.floor(OPAQUE * Mth.lerp(nearHead, TRAIL_SHARE, 1f)), rgb);
    }

    private static void emitSegment(FlatQuadContext quads, ReapSlash slash, double from, double to, Vec3 camera,
                                    int fromColor, int toColor) {
        emit(quads, slash.pointAt(from, OUTER).subtract(camera), fromColor);
        emit(quads, slash.pointAt(to, OUTER).subtract(camera), toColor);
        emit(quads, slash.pointAt(to, INNER).subtract(camera), dimmed(toColor));
        emit(quads, slash.pointAt(from, INNER).subtract(camera), dimmed(fromColor));
    }

    private static int dimmed(int color) {
        return ARGB.color(Mth.floor(ARGB.alpha(color) * INNER_SHARE), color);
    }

    private static void emit(FlatQuadContext quads, Vec3 point, int color) {
        quads.vertex((float) point.x, (float) point.y, (float) point.z, color);
    }
}
