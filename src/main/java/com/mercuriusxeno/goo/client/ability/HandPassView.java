package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Carries a point between the level's view and the first-person hand's
 * pass so it shows on the same spot of the screen. The hand's pass draws
 * after the level, on a cleared depth buffer, in the same camera-relative
 * world axes, but projects at a fixed 70 degrees where the level projects at
 * the player's field of view (GameRenderer.renderItemInHand,
 * Camera.calculateHudFov), so a point keeps its depth along the look and
 * its offsets across the view scale by the ratio of the two spreads.
 * palm-blob-lifts-as-the-spray-source: the lifted blob draws in the hand's pass, over the glove
 */
public final class HandPassView {

    /** The hand pass's field of view, in degrees, before death or fluid narrow it. */
    static final float HAND_FOV = 70f;
    private static final double HALF = 0.5;

    private HandPassView() {
    }

    /**
     * Where a camera-relative point of the level draws in the hand's pass.
     *
     * @param cameraRelative the point less the camera's position, in world axes
     * @param cameraRotation the camera's rotation, from its view onto the world
     * @param worldFov       the level's field of view, in degrees
     * @return the point in the hand pass's camera-relative world axes
     */
    public static Vec3 toHandPass(Vec3 cameraRelative, Quaternionfc cameraRotation, float worldFov) {
        return acrossTheView(cameraRelative, cameraRotation, spread(HAND_FOV) / spread(worldFov));
    }

    /**
     * Where a point the hand's pass draws stands in the level.
     *
     * @param inHand         the point in the hand pass's camera-relative world axes
     * @param cameraRotation the camera's rotation, from its view onto the world
     * @param worldFov       the level's field of view, in degrees
     * @return the point less the camera's position, in world axes
     */
    public static Vec3 fromHandPass(Vec3 inHand, Quaternionfc cameraRotation, float worldFov) {
        return acrossTheView(inHand, cameraRotation, spread(worldFov) / spread(HAND_FOV));
    }

    private static Vec3 acrossTheView(Vec3 point, Quaternionfc cameraRotation, float across) {
        Vector3f view = point.toVector3f().rotate(new Quaternionf(cameraRotation).conjugate());
        view.mul(across, across, 1f);
        return new Vec3(view.rotate(cameraRotation));
    }

    /**
     * How far across the view a point one block deep shows at the view's edge.
     *
     * @param fov the field of view, in degrees
     * @return the tangent of half the field of view
     */
    private static float spread(float fov) {
        return (float) Math.tan(Math.toRadians(fov) * HALF);
    }
}
