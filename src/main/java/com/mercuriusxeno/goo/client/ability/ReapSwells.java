package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.network.ReapSwellPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The swell on the client: a whole sphere of Growth's breeze swelling from
 * its centre out to its radius on an ease-out, then thinning away, drawn
 * through {@code reap_swell.fsh}. Verdant's prism sends it; Reap's slashes
 * draw through {@link ReapSlashBlades}. A swell writes no depth, so it
 * draws on the shared path after the clouds rather than in vanilla's main
 * pass before them (operator ruling 2026-11-18).
 * reap-breeze-harvests-and-replants
 */
public final class ReapSwells {

    /** Ticks a swell takes to thin away once it has reached its radius. */
    static final int FADE_TICKS = 10;
    private static final int OPAQUE = 0xFF;

    private static final List<Swell> LIVE = new ArrayList<>();

    private ReapSwells() {
    }

    /**
     * One swell playing.
     *
     * @param center     where it starts
     * @param radius     the radius it reaches
     * @param swellTicks the ticks it takes to reach it
     * @param startTick  the game tick it started
     */
    record Swell(Vec3 center, float radius, int swellTicks, long startTick) {

        /**
         * How far the swell has reached at a time.
         *
         * @param time the game time with the partial tick
         * @return its radius in blocks
         */
        float radiusAt(float time) {
            float share = Math.clamp((time - startTick) / swellTicks, 0f, 1f);
            return radius * BurnoutGeometry.easeOutCubic(share);
        }

        /**
         * How much of the swell is left at a time: whole as it swells, thinning away after.
         *
         * @param time the game time with the partial tick
         * @return its strength, 0 to 1
         */
        float strengthAt(float time) {
            return 1f - Math.clamp((time - startTick - swellTicks) / FADE_TICKS, 0f, 1f);
        }

        boolean overAt(long tick) {
            return tick - startTick >= swellTicks + FADE_TICKS;
        }
    }

    /**
     * Starts a swell on the client thread.
     *
     * @param payload the swell
     * @param context the network context
     */
    public static void handle(ReapSwellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LIVE.add(new Swell(payload.center(), payload.radius(), payload.swellTicks(), mc.level.getGameTime()));
            }
        });
    }

    /**
     * Drops each swell that has thinned away and hands the shared path one
     * draw per live swell, each sorted by the camera's distance to its
     * centre.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision reap-breeze-harvests-and-replants
     *
     * @param frame the frame being drawn
     * @param draws where each swell's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long tick = mc.level.getGameTime();
        LIVE.removeIf(swell -> swell.overAt(tick));
        float time = tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = frame.levelRenderState().cameraRenderState.pos;
        for (Swell swell : LIVE) {
            float radius = swell.radiusAt(time);
            if (radius > 0f) {
                int color = ARGB.color(ColorBytes.toByte(swell.strengthAt(time)), OPAQUE, 0, 0);
                draws.accept(new SortedDraw(swell.center().distanceToSqr(camera),
                        () -> drawSwell(frame.poseStack(), swell.center().subtract(camera), radius, color)));
            }
        }
    }

    private static void drawSwell(PoseStack poseStack, Vec3 fromCamera, float radius, int color) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        poseStack.pushPose();
        Vec3 corner = fromCamera.subtract(BurnoutGeometry.BLOCK_CENTER, BurnoutGeometry.BLOCK_CENTER,
                BurnoutGeometry.BLOCK_CENTER);
        poseStack.translate(corner.x, corner.y, corner.z);
        BurnoutGeometry.emitSphere(poseStack.last(), buffers.getBuffer(GooRenderTypes.REAP_SWELL_TYPE), radius, color);
        poseStack.popPose();
        buffers.endBatch(GooRenderTypes.REAP_SWELL_TYPE);
    }
}
