package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapBlade;
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
 * Reap's blades on the client, the Crissaegrim's flurry: each a leaf
 * crescent drawn from the very blade the server reaps and cuts with, its
 * bright edge leading and its tips meeting, growing from a skinny sliver at
 * one side to a full crescent ahead, drifting on and thinning away.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReapSlashBlades {

    /** The quads along the crescent, tip to tip. */
    private static final int SEGMENTS = 16;
    /** The pale green the leading edge glows. */
    private static final int EDGE_RGB = 0xE4FFC4;
    private static final int OPAQUE = 0xFF;
    /** The share of the edge's brightness the blade's back glows at. */
    private static final float BODY_SHARE = 0.5f;
    /** Where a tip stands across the blade, the other at its negative. */
    private static final double TIP = 1;
    private static final double TIP_TO_TIP = 2 * TIP;

    private static final List<Swing> LIVE = new ArrayList<>();

    private ReapSlashBlades() {
    }

    /**
     * One blade swinging.
     *
     * @param blade      the blade
     * @param launchTick the game tick it swung
     */
    private record Swing(ReapBlade blade, long launchTick) {

        boolean overAt(long tick) {
            return tick - launchTick > blade.shape().lifeTicks();
        }
    }

    /**
     * Starts a blade on the client thread.
     *
     * @param payload the blade
     * @param context the network context
     */
    public static void handle(ReapSlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LIVE.add(new Swing(payload.blade(), mc.level.getGameTime() + payload.delayTicks()));
            }
        });
    }

    /**
     * Draws every live blade after translucent blocks, dropping each that has thinned away.
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
        LIVE.removeIf(swing -> swing.overAt(tick));
        if (LIVE.isEmpty()) {
            return;
        }
        double time = tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE));
        int leaf = ClientGooTypes.color(GooTypes.LEAF);
        for (Swing swing : LIVE) {
            double age = time - swing.launchTick();
            if (age >= 0) {
                drawBlade(quads, swing.blade(), age, camera, leaf);
            }
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Draws one blade an age into its swing as a crescent: its leading edge
     * glowing pale, its back fading to Leaf's green, its tips meeting.
     *
     * @param quads  the context the quads emit through
     * @param blade  the blade
     * @param age    ticks since it swung, with the partial tick
     * @param camera the camera's position
     * @param leaf   Leaf's green
     */
    private static void drawBlade(FlatQuadContext quads, ReapBlade blade, double age, Vec3 camera, int leaf) {
        int alpha = Mth.floor(blade.strengthAt(age) * OPAQUE);
        int edge = ARGB.color(alpha, EDGE_RGB);
        int body = ARGB.color(Mth.floor(alpha * BODY_SHARE), leaf);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double from = -TIP + TIP_TO_TIP * segment / SEGMENTS;
            double to = -TIP + TIP_TO_TIP * (segment + 1) / SEGMENTS;
            emit(quads, blade.pointAt(age, from, 1).subtract(camera), edge);
            emit(quads, blade.pointAt(age, to, 1).subtract(camera), edge);
            emit(quads, blade.pointAt(age, to, ReapBlade.INNER_SHARE).subtract(camera), body);
            emit(quads, blade.pointAt(age, from, ReapBlade.INNER_SHARE).subtract(camera), body);
        }
    }

    private static void emit(FlatQuadContext quads, Vec3 point, int color) {
        quads.vertex((float) point.x, (float) point.y, (float) point.z, color);
    }
}
