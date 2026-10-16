package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The player's own stretch of drink by the glove is drawn in the level's
 * projection, as the rest of the drink is, so the two meet with no break;
 * the glove's end of the stream is carried to where the level's projection
 * shows the glove the hand's pass draws with its own 70 degree field of view,
 * so the stream still ends on the glove the player sees whatever their field
 * of view (decision diagnose-then-fix-unmake-seam-and-ribbing).
 */
class DrinkHandSightTest {

    private static final float HAND_FOV = 70;
    private static final float NEAR = 0.05f;
    private static final float HAND_FAR = 100;
    private static final float LEVEL_FAR = 512;
    private static final float ASPECT = 16f / 9f;
    private static final double SCREEN_DELTA = 1e-5;
    /** Where the glove's goo sits off the camera, camera-relative, as the hand's pass places it. */
    private static final Vec3 GLOVE = new Vec3(0.42, -0.31, 0.55);

    private static Matrix4f projection(float fov, float far) {
        return new Matrix4f().setPerspective((float) Math.toRadians(fov), ASPECT, NEAR, far);
    }

    private static Matrix4f view() {
        return new Matrix4f().rotationXYZ((float) Math.toRadians(20), (float) Math.toRadians(130) + (float) Math.PI,
                0);
    }

    /**
     * @param projection a projection
     * @param view       a view rotation
     * @param point      a camera-relative point
     * @return where it lands on the screen, x then y in normalized device coordinates
     */
    private static double[] screenOf(Matrix4f projection, Matrix4f view, Vec3 point) {
        Vector4f clip = new Matrix4f(projection).mul(view).transform(new Vector4f((float) point.x, (float) point.y,
                (float) point.z, 1));
        return new double[] {clip.x / clip.w, clip.y / clip.w};
    }

    @Test
    void theGlovesEndOfTheStreamLandsInTheLevelsSightWhereTheHandsPassDrawsTheGlove() {
        for (float levelFov : new float[] {30, 70, 90, 110}) {
            Matrix4f view = view();
            Matrix4f level = projection(levelFov, LEVEL_FAR);

            Vec3 anchor = DrinkRenderer.inLevelSight(GLOVE, view, level, HAND_FOV);

            double[] seen = screenOf(level, view, anchor);
            double[] glove = screenOf(projection(HAND_FOV, HAND_FAR), view, GLOVE);
            assertEquals(glove[0], seen[0], SCREEN_DELTA, "across, at a field of view of " + levelFov);
            assertEquals(glove[1], seen[1], SCREEN_DELTA, "up, at a field of view of " + levelFov);
        }
    }

    @Test
    void atTheHandsOwnFieldOfViewTheGloveStaysPut() {
        Vec3 anchor = DrinkRenderer.inLevelSight(GLOVE, view(), projection(HAND_FOV, LEVEL_FAR), HAND_FOV);

        assertEquals(0, anchor.distanceTo(GLOVE), SCREEN_DELTA);
    }
}
