package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.PortalColor;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.client.throwing.GooTrailParticles;
import com.mercuriusxeno.goo.network.ScanWavePayload;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Portal's shot: a Quantum blob the palm blob shoots in its portal's color,
 * blue from left click and orange from right, drawn as a thrown blob flies:
 * its core, shell and tail streaking behind it and its trail of drips and fog
 * shed each tick, smaller than a thrown blob and flying fast and straight to
 * the face the shot struck, where the portal opens.
 * operator UAT 2026-11-17: have the palm blob shoot a smaller, high velocity blob at the target
 * operator UAT 2026-11-18: the shot is a real thrown blob, smaller and faster
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
     * @param rgb   the shot's portal color, 0xRRGGBB
     */
    private record Darting(ScanWavePayload wave, long start, int rgb) {

        /** @return the shot's velocity, blocks per tick, straight from the palm blob to the face */
        Vec3 velocity() {
            return wave.to().subtract(wave.from()).scale(1.0 / Math.max(1, wave.ticks()));
        }

        /**
         * @param age the ticks since it left, partial ticks included
         * @return where the shot flies then
         */
        Vec3 at(float age) {
            return ScanWaveProgress.at(wave.from(), wave.to(), wave.ticks(), age);
        }
    }

    /**
     * Starts a shot leaving the palm blob, in its portal's color.
     * operator UAT 2026-11-18: quantum goo's color is orange and blue; the shots fire orange on right
     *
     * @param wave     the shot the server sent
     * @param gameTime the client game tick it arrived on
     * @param color    the shot's portal color
     */
    static void start(ScanWavePayload wave, long gameTime, PortalColor color) {
        DARTING.add(new Darting(wave, gameTime, PortalFaces.rgbOf(color)));
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
     * Sheds each flying shot's trail where it flies this tick, as a thrown
     * blob sheds its drips and fog.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || DARTING.isEmpty() || mc.isPaused()) {
            return;
        }
        long now = mc.level.getGameTime();
        for (Darting darting : DARTING) {
            float age = now - darting.start();
            if (!arrived(darting.wave().ticks(), age)) {
                Vec3 velocity = darting.velocity();
                GooTrailParticles.spawnShotTrail(darting.at(age), velocity.normalize(), velocity, darting.rgb());
            }
        }
    }

    /**
     * Hands the shared path each shot where it flies this frame, in the
     * camera's view or a portal view's, each sorted by its own distance, and
     * lets the arrived ones go. The path draws it far to near after the
     * level, clouds included, so no cloud shows through it.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where each shot's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DARTING.clear();
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float now = mc.level.getGameTime() + partial;
        DARTING.removeIf(darting -> arrived(darting.wave().ticks(), now - darting.start()));
        Vec3 camera = PortalView.drawing() ? PortalView.cameraPosition() : mc.gameRenderer.mainCamera().position();
        for (Darting darting : DARTING) {
            Vec3 at = darting.at(now - darting.start());
            draws.accept(new SortedDraw(at.distanceToSqr(camera),
                    () -> drawShot(mc, frame.poseStack(), darting, at.subtract(camera), now)));
        }
    }

    private static void drawShot(Minecraft mc, PoseStack pose, Darting darting, Vec3 offset, float now) {
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        GooFlightRenderer.renderThrownBlob(pose, buffers, GooTypes.QUANTUM, now,
                new GooFlightRenderer.ShotLook(darting.velocity(), SHOT_SIZE, darting.rgb()));
        pose.popPose();
        buffers.endBatch();
    }
}
