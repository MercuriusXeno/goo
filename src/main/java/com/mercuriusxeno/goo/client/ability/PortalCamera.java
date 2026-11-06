package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The camera a portal's view is drawn from: the main camera's lens, set at
 * the point and turn mirrored through the linked pair, its culling frustum
 * rebuilt there.
 * portals-link-by-looking-through-portal-vision
 */
final class PortalCamera extends Camera {

    private static final float NEAR = 0.05F;
    private static final float BLOCKS_PER_CHUNK = 16F;
    private static final float FAR_PER_VIEW_DISTANCE = 4F;

    /**
     * A camera's turn, in its own convention.
     *
     * @param yaw   the yaw, in degrees
     * @param pitch the pitch, in degrees
     * @param roll  the roll, in degrees
     */
    record Turn(float yaw, float pitch, float roll) {
    }

    /**
     * Sets the camera where a portal's view is drawn from.
     *
     * @param level  the level
     * @param player the local player, whose camera this mirrors
     * @param delta  the frame's delta tracker
     * @param fov    the main camera's field of view
     * @param eye    the mirrored eye
     * @param turn   the mirrored turn
     */
    void place(ClientLevel level, LocalPlayer player, DeltaTracker delta, float fov, Vec3 eye, Turn turn) {
        Minecraft minecraft = Minecraft.getInstance();
        setLevel(level);
        setEntity(player);
        update(delta);
        setPosition(eye);
        setRotation(turn.yaw(), turn.pitch(), turn.roll());
        float depthFar = Math.max(minecraft.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK
                * FAR_PER_VIEW_DISTANCE, minecraft.options.cloudRange().get() * BLOCKS_PER_CHUNK);
        setupPerspective(NEAR, depthFar, fov, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        prepareCullFrustum(getViewRotationMatrix(new Matrix4f()), createProjectionMatrixForCulling(), eye);
    }
}
