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
        sampleEnvironment(level, eye);
        setRotation(turn.yaw(), turn.pitch(), turn.roll());
        float depthFar = Math.max(minecraft.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK
                * FAR_PER_VIEW_DISTANCE, minecraft.options.cloudRange().get() * BLOCKS_PER_CHUNK);
        setupPerspective(NEAR, depthFar, fov, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        prepareCullFrustum(getViewRotationMatrix(new Matrix4f()), createProjectionMatrixForCulling(), eye);
    }

    /**
     * Samples the sky and fog colors where the view looks from, once a game
     * tick, as the frame's camera does from its own tick: a camera that never
     * samples reads them black, and draws its sky and fog black. On its first
     * use it samples twice, so the colors it eases between both stand filled.
     *
     * @param level the level
     * @param eye   where the view looks from
     */
    private void sampleEnvironment(ClientLevel level, Vec3 eye) {
        long now = level.getGameTime();
        if (now == sampledAt) {
            return;
        }
        if (sampledAt == NEVER) {
            attributeProbe().tick(level, eye);
        }
        attributeProbe().tick(level, eye);
        sampledAt = now;
    }

    /** The game time this camera last sampled its environment at, or never. */
    private long sampledAt = NEVER;
    private static final long NEVER = Long.MIN_VALUE;

    /**
     * A view through a portal stands apart from the player, so the player's
     * own body draws in it like any other entity: seen from behind through a
     * portal beside it, or down the tunnel a pair facing each other makes.
     *
     * @return always true
     */
    @Override
    public boolean isDetached() {
        return true;
    }
}
