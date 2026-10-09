package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plain prism is one flat-faced, pointed, half-block-wide column standing on the
 * center of its landing face and pointing into the cell, in the prism sprite at the
 * crystal alpha; the landing blob's cube morphs into that column, its goo look fading
 * into the quartz (decision prism-is-one-pointed-quartz-column).
 */
class PrismCrystalTest {

    private static final double EPSILON = 1e-6;
    private static final int SIDES = 6;

    @Nested
    class Shape {

        @Test
        void theColumnIsOneFlatFacedPrismEightPixelsAcrossWithAPoint() {
            assertEquals(1, PrismCrystal.PRISMS.size());
            CrystalCluster.Prism prism = PrismCrystal.PRISMS.getFirst();
            assertEquals(4, prism.radius());
            assertEquals(0, prism.rounding());
            assertEquals(0, prism.tilt());
            assertTrue(prism.tipLength() > 0, "the column has no point");
        }

        @Test
        void sixSidesAndSixTipFacesCloseOnOnePointAtTheColumnsLength() {
            CrystalCluster.Prism prism = PrismCrystal.PRISMS.getFirst();
            List<Vec3[]> faces = CrystalClusterSubmitter.prismFaces(prism);
            assertEquals(2 * SIDES, faces.size());
            Vec3 point = new Vec3(CrystalCluster.BASE_X, CrystalCluster.BASE_Y + prism.length(), CrystalCluster.BASE_Z);
            int tipFaces = 0;
            for (Vec3[] face : faces) {
                if (face[2].equals(face[3])) {
                    tipFaces++;
                    assertTrue(face[2].distanceTo(point) < EPSILON, "a tip face closes off the point at " + face[2]);
                }
            }
            assertEquals(SIDES, tipFaces);
        }
    }

    @Nested
    class Placement {

        @Test
        void eachFacingStandsTheBaseOnItsLandingFacesCenterPointingIntoTheCell() {
            Vector3f base = new Vector3f((float) CrystalCluster.BASE_X, (float) CrystalCluster.BASE_Y,
                    (float) CrystalCluster.BASE_Z).div(16);
            for (Direction facing : Direction.values()) {
                Direction landing = facing.getOpposite();
                Matrix4f placement = PrismColumn.placement(facing);
                Vector3f placedBase = placement.transformPosition(new Vector3f(base));
                Vector3f landingCenter = new Vector3f(0.5f + 0.5f * landing.getStepX(),
                        0.5f + 0.5f * landing.getStepY(), 0.5f + 0.5f * landing.getStepZ());
                assertVectorEquals(landingCenter, placedBase, "base for " + facing);
                Vector3f axis = placement.transformDirection(new Vector3f(0, 1, 0));
                Vector3f intoCell = new Vector3f(-landing.getStepX(), -landing.getStepY(), -landing.getStepZ());
                assertVectorEquals(intoCell, axis, "axis for " + facing);
            }
        }

        private static void assertVectorEquals(Vector3f expected, Vector3f actual, String what) {
            assertEquals(expected.x, actual.x, EPSILON, what + " x");
            assertEquals(expected.y, actual.y, EPSILON, what + " y");
            assertEquals(expected.z, actual.z, EPSILON, what + " z");
        }
    }

    @Nested
    class Morph {

        private static final Vec3 BLOB_CENTER = new Vec3(CrystalCluster.BASE_X,
                CrystalCluster.BASE_Y + PrismCrystal.BLOB_HALF_WIDTH, CrystalCluster.BASE_Z);

        private static boolean holds(List<Vec3[]> faces, Vec3 point) {
            return faces.stream().flatMap(Arrays::stream).anyMatch(corner -> corner.distanceTo(point) < EPSILON);
        }

        @Test
        void theMorphStartsAsTheBlobsCubeSittingOnTheStruckFace() {
            double half = PrismCrystal.BLOB_HALF_WIDTH;
            List<Vec3[]> cube = PrismCrystal.morphFaces(0);
            for (Vec3[] face : cube) {
                for (Vec3 corner : face) {
                    Vec3 off = corner.subtract(BLOB_CENTER);
                    double chebyshev = Math.max(Math.abs(off.x), Math.max(Math.abs(off.y), Math.abs(off.z)));
                    assertEquals(half, chebyshev, EPSILON, "a corner off the cube at " + corner);
                }
            }
            for (int x = -1; x <= 1; x += 2) {
                for (int y = -1; y <= 1; y += 2) {
                    for (int z = -1; z <= 1; z += 2) {
                        Vec3 cubeCorner = BLOB_CENTER.add(x * half, y * half, z * half);
                        assertTrue(holds(cube, cubeCorner), "the cube lacks its corner " + cubeCorner);
                    }
                }
            }
        }

        @Test
        void theMorphEndsAsTheColumnWithItsPoint() {
            CrystalCluster.Prism prism = PrismCrystal.PRISMS.getFirst();
            List<Vec3[]> column = PrismCrystal.morphFaces(1);
            for (Vec3[] face : CrystalClusterSubmitter.prismFaces(prism)) {
                for (Vec3 corner : face) {
                    assertTrue(holds(column, corner), "the morph's end lacks the column's corner " + corner);
                }
            }
            Vec3 axis = new Vec3(CrystalCluster.BASE_X, 0, CrystalCluster.BASE_Z);
            for (Vec3[] face : column) {
                for (Vec3 corner : face) {
                    double fromAxis = new Vec3(corner.x, 0, corner.z).distanceTo(axis);
                    assertTrue(fromAxis <= prism.radius() + EPSILON, "a corner outside the column at " + corner);
                    assertTrue(corner.y >= CrystalCluster.BASE_Y - EPSILON
                            && corner.y <= CrystalCluster.BASE_Y + prism.length() + EPSILON,
                            "a corner beyond the column's length at " + corner);
                }
            }
        }

        @Test
        void aLookFadesToItsShareOfItsOpacity() {
            GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);
            CrystalClusterSubmitter.Look goo = new CrystalClusterSubmitter.Look(uv, ARGB.color(0xE0, 0x40, 0x80, 0xC0));
            CrystalClusterSubmitter.Look faded = PrismCrystal.fade(goo, 0.25f);
            assertEquals(ARGB.color(0x38, 0x40, 0x80, 0xC0), faded.color());
        }
    }

    @Test
    void theLookIsThePrismSpriteUntintedAtTheCrystalAlpha() {
        assertEquals("goo:block/prism", PrismCrystal.SPRITE.toString());
        GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);
        CrystalClusterSubmitter.Look look = PrismCrystal.lookOf(uv);
        assertEquals(ARGB.color(0xE0, 0xFF, 0xFF, 0xFF), look.color());
    }

    /**
     * The sprite's frame texels are more opaque than its interior; tiled, they stood
     * as a two-texel band at every seam, so the look samples one texel inside them.
     */
    @Test
    void theLookSamplesInsideTheSpritesOneTexelFrame() {
        float texel = 0.125f / 16;
        GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);
        GooRenderUtil.UvRect interior = PrismCrystal.lookOf(uv).uv();
        assertEquals(0.25f + texel, interior.u0(), 1e-7);
        assertEquals(0.5f + texel, interior.v0(), 1e-7);
        assertEquals(0.375f - texel, interior.u1(), 1e-7);
        assertEquals(0.625f - texel, interior.v1(), 1e-7);
    }
}
