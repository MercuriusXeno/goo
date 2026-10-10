package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ReapSwellPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Reap's swell on the client: a whole sphere of Growth's breeze swelling
 * from where the blob struck out to Reap's radius on an ease-out, then
 * thinning away, drawn through {@code reap_swell.fsh}.
 * reap-breeze-harvests-and-replants
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
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
     * Draws every live swell, dropping each that has thinned away.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long tick = mc.level.getGameTime();
        LIVE.removeIf(swell -> swell.overAt(tick));
        float time = tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        for (Swell swell : LIVE) {
            float radius = swell.radiusAt(time);
            if (radius <= 0f) {
                continue;
            }
            int color = ARGB.color(NetherDiscMesh.toByte(swell.strengthAt(time)), OPAQUE, 0, 0);
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            Vec3 corner = swell.center().subtract(camera).subtract(BurnoutGeometry.BLOCK_CENTER,
                    BurnoutGeometry.BLOCK_CENTER, BurnoutGeometry.BLOCK_CENTER);
            poseStack.translate(corner.x, corner.y, corner.z);
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, GooRenderTypes.REAP_SWELL_TYPE,
                    (pose, consumer) -> BurnoutGeometry.emitSphere(pose, consumer, radius, color));
            poseStack.popPose();
        }
    }
}
