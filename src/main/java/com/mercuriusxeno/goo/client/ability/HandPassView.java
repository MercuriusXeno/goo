package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
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
 * its offsets across the view scale by the ratio of the two spreads; the
 * level's projection also carries the view's bob, which the point takes with it.
 * palm-blob-lifts-as-the-spray-source: the lifted blob draws in the hand's pass, over the glove
 */
public final class HandPassView {

    /** The hand pass's field of view, in degrees, before death or fluid narrow it. */
    static final float HAND_FOV = 70f;
    private static final double HALF = 0.5;

    private HandPassView() {
    }

    /**
     * Where a camera-relative point of the level draws in the hand's pass:
     * the level shows it bobbed, since its projection carries the view's bob,
     * while a point the hand pass places stands after that pass's own bob, so
     * the bob is carried across with the point.
     * palm-blob-lifts-as-the-spray-source: the drips leave the blob as the screen shows it, walking or still
     *
     * @param cameraRelative the point less the camera's position, in world axes
     * @param cameraRotation the camera's rotation, from its view onto the world
     * @param worldFov       the level's field of view, in degrees
     * @param viewBob        the frame's hurt and walking bob, in view space
     * @return the point in the hand pass's camera-relative world axes
     */
    public static Vec3 toHandPass(Vec3 cameraRelative, Quaternionfc cameraRotation, float worldFov,
                                  Matrix4fc viewBob) {
        Vector3f view = intoTheView(cameraRelative, cameraRotation);
        viewBob.transformPosition(view);
        float across = spread(HAND_FOV) / spread(worldFov);
        return outOfTheView(view.mul(across, across, 1f), cameraRotation);
    }

    /**
     * Where a point the hand's pass draws stands in the level, the frame's bob taken back out.
     *
     * @param inHand         the point in the hand pass's camera-relative world axes
     * @param cameraRotation the camera's rotation, from its view onto the world
     * @param worldFov       the level's field of view, in degrees
     * @param viewBob        the frame's hurt and walking bob, in view space
     * @return the point less the camera's position, in world axes
     */
    public static Vec3 fromHandPass(Vec3 inHand, Quaternionfc cameraRotation, float worldFov, Matrix4fc viewBob) {
        float across = spread(worldFov) / spread(HAND_FOV);
        Vector3f view = intoTheView(inHand, cameraRotation).mul(across, across, 1f);
        return outOfTheView(viewBob.invert(new Matrix4f()).transformPosition(view), cameraRotation);
    }

    /**
     * The frame's view bob as GameRenderer.renderLevel multiplies it into the
     * level's projection: the hurt shake, then the walking bob where the
     * options keep it.
     *
     * @param mc     the client
     * @param camera the camera's render state the frame bobs
     * @return the bob, in view space
     */
    public static Matrix4f viewBobOf(Minecraft mc, CameraRenderState camera) {
        PoseStack bob = new PoseStack();
        mc.gameRenderer.bobHurt(camera, bob);
        if (mc.gameRenderer.gameRenderState.optionsRenderState.bobView) {
            mc.gameRenderer.bobView(camera, bob);
        }
        return new Matrix4f(bob.last().pose());
    }

    /**
     * The main camera's view bob this frame.
     *
     * @param mc the client
     * @return the bob, in view space
     */
    static Matrix4f frameBob(Minecraft mc) {
        return viewBobOf(mc, mc.gameRenderer.gameRenderState.levelRenderState.cameraRenderState);
    }

    private static Vector3f intoTheView(Vec3 point, Quaternionfc cameraRotation) {
        return point.toVector3f().rotate(new Quaternionf(cameraRotation).conjugate());
    }

    private static Vec3 outOfTheView(Vector3f view, Quaternionfc cameraRotation) {
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
