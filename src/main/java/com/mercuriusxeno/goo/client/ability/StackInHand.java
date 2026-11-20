package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The local first-person player's held stack, Knives' pyramid or Dart's
 * triangle, submitted in the hand's pass with the lifted blob it hovers
 * over: laid out as the level lays a caster's stack, in camera-relative world
 * axes, and carried onto the hand pass through the one transform the blob's
 * own place takes, so it draws on the blob, before the eyes, over the world.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
public final class StackInHand {

    private StackInHand() {
    }

    /**
     * Submits the local player's stack in the hand's pass, from the glove's
     * palm draw while it stands before the eyes.
     *
     * @param poseStack the glove's pose stack in the hand's pass
     * @param collector the hand pass's node collector
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.mainCamera();
        Matrix4f toHand = HandPassView.toHandPassMatrix(camera.rotation(), camera.getFov(), HandPassView.frameBob(mc));
        poseStack.pushPose();
        poseStack.last().pose().set(toHand);
        poseStack.last().normal().set(new Matrix3f(toHand).invert().transpose());
        HoveringKnives.submitInHand(poseStack, collector, player, partial, camera.position());
        HeldJavelins.submitInHand(poseStack, collector, player, partial, camera.position());
        poseStack.popPose();
    }
}