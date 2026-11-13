package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooJoinPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The joins running on this client: each absorbed goo cuboid drawn flowing
 * into its anchor over a short ease, and the anchor's drawn size easing from
 * its own volume's to the summed volume's.
 * decision combining-flecks-join-into-one-cuboid
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooJoins {

    /** How long a join's ease runs, in ticks. */
    static final float JOIN_TICKS = 6f;
    /** The smoothstep's terms, 3s² - 2s³, slow at both ends of the ease. */
    private static final float SMOOTHSTEP_RISE = 3f;
    private static final float SMOOTHSTEP_EASE = 2f;
    private static final double HALF = 0.5;

    /** Each running join by its anchor's entity id. */
    private static final Map<Integer, Running> RUNNING = new ConcurrentHashMap<>();

    private GooJoins() {
    }

    /**
     * A join on this client and the goo type it draws in.
     *
     * @param join  the join's geometry, in the level
     * @param type  the anchor's goo type
     * @param began the game time the join reached this client
     */
    private record Running(GooJoin join, ResourceKey<GooTypeDefinition> type, double began) {

        float shareAt(double now) {
            float linear = (float) Math.clamp((now - began) / JOIN_TICKS, 0d, 1d);
            return linear * linear * (SMOOTHSTEP_RISE - SMOOTHSTEP_EASE * linear);
        }
    }

    /**
     * Starts the join a payload names, on the client thread, once its anchor
     * stands in the client's level.
     *
     * @param payload the join payload
     * @param context the network context
     */
    public static void handle(GooJoinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.level.getEntity(payload.anchorId()) instanceof ItemEntity anchor)) {
                return;
            }
            ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(anchor.getItem());
            if (type == null) {
                return;
            }
            RUNNING.put(payload.anchorId(), new Running(joinOf(payload), type, mc.level.getGameTime()));
        });
    }

    /**
     * The join's geometry in the level from a payload: every place a
     * cuboid's center, resting on the point its goo item stands at.
     *
     * @param payload the join payload
     * @return the join
     */
    static GooJoin joinOf(GooJoinPayload payload) {
        int absorbedVolume = 0;
        List<GooJoin.Absorbed> absorbed = new ArrayList<>(payload.absorbed().size());
        for (GooJoinPayload.Absorbed each : payload.absorbed()) {
            absorbed.add(new GooJoin.Absorbed(centerAbove(each.place(), each.volume()), each.volume()));
            absorbedVolume += each.volume();
        }
        int fromVolume = payload.joinedVolume() - absorbedVolume;
        return new GooJoin(centerAbove(payload.anchorPlace(), fromVolume), fromVolume, payload.joinedVolume(),
                List.copyOf(absorbed));
    }

    /**
     * A cuboid's center resting on the point its goo item stands at.
     *
     * @param feet   where the goo item stands
     * @param volume the goo it holds, in mB
     * @return the center
     */
    private static Vec3 centerAbove(Vec3 feet, double volume) {
        return feet.add(0, GooCuboidSize.halfWidthInLevel(GooCuboidSize.sizeOf(volume)), 0);
    }

    /**
     * The size a join draws a goo item's stack at while the stack is its
     * running join's anchor's.
     *
     * @param stack the stack the cuboid renderer draws
     * @return the anchor's eased size, or null for a stack no running join anchors
     */
    static @Nullable Float anchorSizeOf(ItemStack stack) {
        if (RUNNING.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Map.Entry<Integer, Running> entry : RUNNING.entrySet()) {
            Entity anchor = mc.level.getEntity(entry.getKey());
            if (anchor instanceof ItemEntity item && item.getItem() == stack) {
                Running running = entry.getValue();
                return running.join().drawnAt(running.shareAt(now)).anchorSize();
            }
        }
        return null;
    }

    /**
     * Draws every running join's absorbed cuboids flowing into their
     * anchors, and forgets each join whose ease has run.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RUNNING.isEmpty()) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RUNNING.values().removeIf(running -> now - running.began() > JOIN_TICKS);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Running running : RUNNING.values()) {
            for (GooJoin.Bridge bridge : running.join().drawnAt(running.shareAt(now)).bridges()) {
                drawBridge(event.getPoseStack(), buffers, running.type(), bridge, camera, (float) now);
            }
        }
        buffers.endBatch();
    }

    /**
     * Draws a bridge as the palm cuboid stretched over the axis-aligned hull
     * of its leading and trailing cubes, its faces on the world's axes.
     *
     * @param poseStack the level's pose stack
     * @param buffers   the buffer source
     * @param type      the goo type
     * @param bridge    the bridge
     * @param camera    the camera's place
     * @param gameTime  the game time with the frame's partial tick
     */
    private static void drawBridge(PoseStack poseStack, MultiBufferSource buffers,
                                   ResourceKey<GooTypeDefinition> type, GooJoin.Bridge bridge, Vec3 camera,
                                   float gameTime) {
        double halfWidth = GooCuboidSize.halfWidthInLevel(bridge.size());
        if (halfWidth <= 0) {
            return;
        }
        Vec3 center = bridge.leading().add(bridge.trailing()).scale(HALF);
        Vec3 halfSpan = bridge.leading().subtract(bridge.trailing()).scale(HALF);
        poseStack.pushPose();
        poseStack.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
        poseStack.scale(stretch(halfSpan.x, halfWidth), stretch(halfSpan.y, halfWidth),
                stretch(halfSpan.z, halfWidth));
        GooFlightRenderer.emitCore(poseStack.last(), buffers.getBuffer(GooSubmitter.solidOnBlockAtlas()), type,
                gameTime, 1f);
        GooFlightRenderer.emitShell(poseStack.last(), buffers.getBuffer(GooSubmitter.renderType()), type, 1f);
        poseStack.popPose();
    }

    /**
     * How far a cuboid of a thrown blob's size stretches along an axis to
     * span a bridge's half extent there.
     *
     * @param halfSpan  half the distance between the cubes' centers along the axis
     * @param halfWidth each cube's half width
     * @return the axis's scale
     */
    private static float stretch(double halfSpan, double halfWidth) {
        return (float) ((Math.abs(halfSpan) + halfWidth) / GooFlightRenderer.SHELL_HW);
    }
}
