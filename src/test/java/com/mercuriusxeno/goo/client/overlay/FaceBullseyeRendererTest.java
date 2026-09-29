package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bullseye's rings lie on a block target's struck face, centered on the
 * face center, and only a block target draws one (decision
 * aim-arc-ends-in-face-bullseye).
 */
class FaceBullseyeRendererTest {

    private static final double EPSILON = 1e-9;
    private static final BlockPos POS = new BlockPos(3, 64, -7);
    private static final double RADIUS = 0.3;

    private static double along(Vec3 point, Direction.Axis axis) {
        return point.get(axis);
    }

    @Nested
    class RingGeometry {
        @ParameterizedTest
        @EnumSource(Direction.class)
        void ringLiesInTheFacePlaneJustOutsideTheBlock(Direction face) {
            Vec3 faceCenter = new TargetResult.BlockTarget(POS, face, false).resolveEndpoint();
            Direction.Axis normal = face.getAxis();
            double facePlane = along(faceCenter, normal);
            double outward = face.getAxisDirection().getStep();
            for (Vec3 point : FaceBullseyeRenderer.ringPoints(faceCenter, face, RADIUS, 16)) {
                double offFace = (along(point, normal) - facePlane) * outward;
                assertTrue(offFace > 0 && offFace <= FaceBullseyeRenderer.FACE_NUDGE + EPSILON,
                        () -> face + " point " + point + " sits " + offFace + " off the face");
            }
        }

        @ParameterizedTest
        @EnumSource(Direction.class)
        void ringIsCenteredOnTheFaceCenter(Direction face) {
            Vec3 faceCenter = new TargetResult.BlockTarget(POS, face, false).resolveEndpoint();
            int segments = 16;
            Vec3[] points = FaceBullseyeRenderer.ringPoints(faceCenter, face, RADIUS, segments);
            Vec3 sum = Vec3.ZERO;
            for (int i = 0; i < segments; i++) {
                sum = sum.add(points[i]);
            }
            Vec3 centroid = sum.scale(1.0 / segments);
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (axis != face.getAxis()) {
                    assertEquals(along(faceCenter, axis), along(centroid, axis), EPSILON, face + " on " + axis);
                }
            }
            for (int i = 0; i < segments; i++) {
                Vec3 inPlane = points[i].subtract(centroid);
                assertEquals(RADIUS, inPlane.length(), EPSILON, face + " point " + i);
            }
        }

        @Test
        void ringClosesOnItsFirstPoint() {
            Vec3[] points = FaceBullseyeRenderer.ringPoints(Vec3.ZERO, Direction.UP, RADIUS, 16);
            assertEquals(17, points.length);
            assertEquals(0, points[0].distanceTo(points[16]), EPSILON);
        }
    }

    @Nested
    class WhichTargetsDraw {
        @ParameterizedTest
        @EnumSource(Direction.class)
        void blockTargetMarksItsStruckFace(Direction face) {
            assertEquals(face, FaceBullseyeRenderer.bullseyeFace(new TargetResult.BlockTarget(POS, face, false)));
        }

        @Test
        void chainMarkerDrawsNoBullseye() {
            assertNull(FaceBullseyeRenderer.bullseyeFace(new TargetResult.ChainMarkerTarget(POS)));
        }

        @Test
        void glowCrystalDrawsNoBullseye() {
            assertNull(FaceBullseyeRenderer.bullseyeFace(new TargetResult.GlowCrystalTarget(POS, Direction.UP)));
        }

        @Test
        void entityDrawsNoBullseye() {
            assertNull(FaceBullseyeRenderer.bullseyeFace(new TargetResult.EntityTarget(null)));
        }
    }

    @Test
    void everyRingStaysInsideTheFace() {
        for (double radius : FaceBullseyeRenderer.RING_RADII) {
            assertTrue(radius > 0 && radius < 0.5, () -> "radius " + radius);
        }
    }
}
