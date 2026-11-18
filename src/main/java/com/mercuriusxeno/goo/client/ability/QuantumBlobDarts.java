package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.network.ScanWavePayload;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Portal's shot: a small Quantum blob the palm blob shoots, flying fast and
 * straight to the face the shot struck, where the portal opens.
 * operator UAT 2026-11-17: have the palm blob shoot a smaller, high velocity blob at the target
 * shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob
 * portal-has-no-bullet-drop-and-goes-where-you-point-like-bulb
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class QuantumBlobDarts {

    /** The shot blob's size against a thrown blob's: smaller, as the operator asked. */
    static final float SHOT_SIZE = 0.5f;

    private static final List<Darting> DARTING = new CopyOnWriteArrayList<>();

    private QuantumBlobDarts() {
    }

    /**
     * One shot on its way and the tick it left.
     *
     * @param wave  the shot the server sent
     * @param start the client game tick it left the palm blob
     */
    private record Darting(ScanWavePayload wave, long start) {
    }

    /**
     * Starts a shot leaving the palm blob.
     *
     * @param wave     the shot the server sent
     * @param gameTime the client game tick it arrived on
     */
    static void start(ScanWavePayload wave, long gameTime) {
        DARTING.add(new Darting(wave, gameTime));
    }

    /**
     * Whether a shot has reached the struck face.
     *
     * @param ticks the ticks the shot takes
     * @param age   the ticks since it left, partial ticks included
     * @return true once it has arrived
     */
    static boolean arrived(int ticks, float age) {
        return age >= ticks;
    }

    /**
     * Draws each shot where it flies this frame and lets the arrived ones go.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DARTING.clear();
            return;
        }
        if (DARTING.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float now = mc.level.getGameTime() + partial;
        Vec3 camera = PortalView.drawing() ? PortalView.cameraPosition() : mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        DARTING.removeIf(darting -> arrived(darting.wave().ticks(), now - darting.start()));
        for (Darting darting : DARTING) {
            ScanWavePayload wave = darting.wave();
            Vec3 at = ScanWaveProgress.at(wave.from(), wave.to(), wave.ticks(), now - darting.start());
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            GooFlightRenderer.renderBlob(pose, buffers, GooTypes.QUANTUM, now, SHOT_SIZE);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
