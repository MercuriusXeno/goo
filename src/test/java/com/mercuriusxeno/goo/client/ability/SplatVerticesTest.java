package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each splat vertex carries its offset from the hit point in the model's own
 * space, so the splat rides the body wherever the pose carries the mob.
 */
class SplatVerticesTest {

    private static final float EPSILON = 1e-4f;
    private static final Vec3 CAMERA = new Vec3(100, 64, -40);

    /** A mob root pose: camera-relative placement, a body turn and the model flip. */
    private static Matrix4f rootPose(float x, float y, float z, float bodyTurnRadians) {
        return new Matrix4f().translate(x, y, z).rotateY(bodyTurnRadians).scale(-1, -1, 1).translate(0, -1.501f, 0);
    }

    private static Vector3f offsetOf(Matrix4f rootPose, Vector3f pinnedHit, Vector3f modelVertex) {
        Vector3f posed = rootPose.transformPosition(modelVertex, new Vector3f());
        return SplatVertices.splatOffset(new Matrix4f(rootPose).invert(), pinnedHit, posed.x, posed.y, posed.z,
                new Vector3f());
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    @Nested
    class Offset {

        @Test
        void vertexAtTheHitPointReadsNoOffset() {
            Matrix4f pose = rootPose(3, -1, 5, 0.7f);
            Vector3f hit = new Vector3f(0.2f, 0.4f, -0.3f);

            assertVector(new Vector3f(), offsetOf(pose, hit, hit));
        }

        @Test
        void vertexAKnownDistanceAwayReadsThatDistance() {
            Matrix4f pose = rootPose(3, -1, 5, 0.7f);
            Vector3f hit = new Vector3f(0.2f, 0.4f, -0.3f);
            Vector3f vertex = new Vector3f(hit).add(0, 0.3f, 0.4f);

            assertEquals(0.5f, offsetOf(pose, hit, vertex).length(), EPSILON);
        }

        @Test
        void movingAndTurningTheMobLeavesTheOffsetUnchanged() {
            Vector3f hit = new Vector3f(0.2f, 0.4f, -0.3f);
            Vector3f vertex = new Vector3f(-0.1f, 0.9f, 0.25f);

            Vector3f before = offsetOf(rootPose(3, -1, 5, 0.7f), hit, vertex);
            Vector3f after = offsetOf(rootPose(-8, 2, 1, 2.9f), hit, vertex);

            assertVector(before, after);
        }
    }

    @Nested
    class Pin {

        @Test
        void pinnedHitPosesBackOntoTheWorldHitPoint() {
            Matrix4f pose = rootPose(3, -1, 5, 0.7f);
            Vec3 hitPoint = new Vec3(103.5, 63.2, -35.25);

            Vector3f pinned = MobCoatLayer.pinHit(pose, hitPoint, CAMERA);

            Vector3f posedBack = pose.transformPosition(pinned, new Vector3f());
            assertVector(new Vector3f(3.5f, -0.8f, 4.75f), posedBack);
        }
    }

    @Nested
    class Packing {

        @Test
        void offsetPacksToTheShadersUnitsAndClampsToAShort() {
            assertEquals(256, SplatVertices.offsetUnits(0.25f));
            assertEquals(-1024, SplatVertices.offsetUnits(-1f));
            assertEquals(Short.MAX_VALUE, SplatVertices.offsetUnits(1000f));
            assertEquals(Short.MIN_VALUE, SplatVertices.offsetUnits(-1000f));
        }
    }
}
