package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A point carried between the level's view and the first-person hand's pass
 * lands on the same spot of the screen at the same depth: the hand pass
 * projects at a fixed 70 degrees while the level projects at the player's
 * field of view, both in camera-relative world axes
 * (GameRenderer.renderItemInHand, Camera.calculateHudFov).
 * palm-blob-lifts-as-the-spray-source
 */
class HandPassViewTest {

    private static final float WORLD_FOV = 95f;
    private static final float HUD_FOV = 70f;
    private static final double DELTA = 1e-5;
    /** Where the spray source stands from the eye: ahead, out to the right and a little down. */
    private static final Vec3 OFF_THE_EYE = new Vec3(-0.3, -0.5, 0.6);

    /** A camera looking thirty degrees down and a little west of south, as Camera.setRotation turns it. */
    private static Quaternionf cameraTurned() {
        return new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(20),
                -(float) Math.toRadians(30), 0f);
    }

    /** Where a camera-relative point lands on the screen, and how deep, under a field of view. */
    private static Vec3 screenAt(Vec3 cameraRelative, Quaternionf camera, float fov) {
        Vector3f view = cameraRelative.toVector3f().rotate(new Quaternionf(camera).conjugate());
        double spread = Math.tan(Math.toRadians(fov) / 2);
        return new Vec3(view.x / -view.z / spread, view.y / -view.z / spread, view.z);
    }

    @Test
    void aPointCarriedIntoTheHandPassLandsOnTheSameSpotOfTheScreen() {
        Quaternionf camera = cameraTurned();

        Vec3 inHand = HandPassView.toHandPass(OFF_THE_EYE, camera, WORLD_FOV, new Matrix4f());

        Vec3 seen = screenAt(OFF_THE_EYE, camera, WORLD_FOV);
        Vec3 drawn = screenAt(inHand, camera, HUD_FOV);
        assertEquals(seen.x, drawn.x, DELTA, "across the screen");
        assertEquals(seen.y, drawn.y, DELTA, "up the screen");
        assertEquals(seen.z, drawn.z, DELTA, "depth");
    }

    /** A walking view's bob as GameRenderer.bobView turns it: down and aside, rolled and pitched a few degrees. */
    private static Matrix4f walkingBob() {
        return new Matrix4f().translate(0.04f, -0.08f, 0f).rotateZ((float) Math.toRadians(2.4))
                .rotateX((float) Math.toRadians(3.5));
    }

    /** Where a view-space point lands on the screen under a field of view, and how deep. */
    private static Vec3 projected(Vector3f view, float fov) {
        double spread = Math.tan(Math.toRadians(fov) / 2);
        return new Vec3(view.x / -view.z / spread, view.y / -view.z / spread, view.z);
    }

    @Test
    void aPointCarriedIntoTheHandPassLandsWhereTheBobbedLevelDrawsIt() {
        Quaternionf camera = cameraTurned();
        Matrix4f bob = walkingBob();

        Vec3 inHand = HandPassView.toHandPass(OFF_THE_EYE, camera, WORLD_FOV, bob);

        // GameRenderer.renderLevel multiplies the level's projection by the bob; the lifted blob stands where the
        // hand pass's pose puts it, after that pass's own bob, so it shows at its point unbobbed
        Vec3 seen = projected(bob.transformPosition(
                OFF_THE_EYE.toVector3f().rotate(new Quaternionf(camera).conjugate())), WORLD_FOV);
        Vec3 drawn = screenAt(inHand, camera, HUD_FOV);
        assertEquals(seen.x, drawn.x, DELTA, "across the screen");
        assertEquals(seen.y, drawn.y, DELTA, "up the screen");
    }

    @Test
    void aPointCarriedOutOfTheHandPassComesBackWhereItWas() {
        Quaternionf camera = cameraTurned();

        Vec3 back = HandPassView.fromHandPass(HandPassView.toHandPass(OFF_THE_EYE, camera, WORLD_FOV, walkingBob()),
                camera, WORLD_FOV, walkingBob());

        assertEquals(0, back.distanceTo(OFF_THE_EYE), DELTA);
    }
}
