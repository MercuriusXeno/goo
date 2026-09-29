package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystal's faces tile the growing type's sprite by their place in the block, two
 * texels per model pixel, cut at the sprite's edges so the texture repeats rather than
 * clamps, and read one plane however rounding turns their normal (decision
 * crystallizer-emits-chrysm); the materia orb's faces sit on its sphere, turned out
 * (decision chrysm-tiers-in-32x-steps).
 */
class CrystalClusterSubmitterTest {

    private static final Vec3 FACING_Z = new Vec3(0, 0, 1);
    private static final double SPRITE = 16;
    private static final double EPSILON = 1e-9;

    private static Vec3[] faceAtZ(double x0, double x1, double y0, double y1) {
        return new Vec3[] {new Vec3(x0, y0, 8), new Vec3(x1, y0, 8), new Vec3(x1, y1, 8), new Vec3(x0, y1, 8)};
    }

    @Test
    void theOrbsFacesSitOnItsSphereAndFaceOutward() {
        Vec3 center = new Vec3(8, 20, 8);
        double radius = CrystalCluster.ORB_RADIUS;
        for (Vec3[] face : CrystalClusterSubmitter.orbFaces(center, radius)) {
            Vec3 middle = Vec3.ZERO;
            for (Vec3 corner : face) {
                assertEquals(radius, corner.distanceTo(center), 1e-6, "a corner off the sphere");
                middle = middle.add(corner.scale(1.0 / face.length));
            }
            assertTrue(CrystalClusterSubmitter.faceNormal(face).dot(middle.subtract(center)) > 0,
                    "a face turned inward at " + middle);
        }
    }

    @Test
    void aFaceReadsItsPlaceInTheBlockAtTwoTexelsPerPixel() {
        List<List<CrystalClusterSubmitter.TexelPoint>> pieces =
                CrystalClusterSubmitter.tilePieces(faceAtZ(4, 6, 20, 24), FACING_Z);
        assertEquals(1, pieces.size(), "a face within one tile stays whole");
        for (CrystalClusterSubmitter.TexelPoint point : pieces.getFirst()) {
            assertEquals(point.pos().x * 2, point.u(), EPSILON, "u reads x at two texels a pixel");
            assertEquals((16 - (point.pos().y - CrystalCluster.BASE_Y)) * 2 - SPRITE, point.v(), EPSILON,
                    "v reads the height down from the top at two texels a pixel, within one sprite");
        }
    }

    @Test
    void aFaceAcrossASpriteEdgeIsCutNotClamped() {
        List<List<CrystalClusterSubmitter.TexelPoint>> pieces =
                CrystalClusterSubmitter.tilePieces(faceAtZ(6, 10, 20, 24), FACING_Z);
        assertEquals(2, pieces.size(), "u 12 to 20 crosses the edge at 16");
        double area = 0;
        for (List<CrystalClusterSubmitter.TexelPoint> piece : pieces) {
            for (CrystalClusterSubmitter.TexelPoint point : piece) {
                double wrapped = point.pos().x * 2 % SPRITE;
                assertTrue(Math.abs(point.u() - wrapped) < EPSILON || Math.abs(point.u() - SPRITE) < EPSILON
                                && wrapped < EPSILON, "u " + point.u() + " should repeat x's texel " + wrapped);
            }
            area += areaOf(piece);
        }
        assertEquals(4 * 4, area, EPSILON, "the pieces together cover the face");
    }

    @Test
    void facesAtDifferentPlacesReadDifferentPartsOfTheSprite() {
        double left = CrystalClusterSubmitter.tilePieces(faceAtZ(2, 4, 18, 22), FACING_Z).getFirst().getFirst().u();
        double right = CrystalClusterSubmitter.tilePieces(faceAtZ(11, 13, 18, 22), FACING_Z).getFirst().getFirst().u();
        assertNotEquals(left, right, "two faces apart should not show the same sliver");
    }

    @Test
    void aFaceTurnedEvenlyBetweenXAndZReadsOnePlaneWhateverTheRounding() {
        Vec3[] face = {new Vec3(3, 18, 5), new Vec3(5, 18, 3), new Vec3(5, 22, 3), new Vec3(3, 22, 5)};
        Vec3 leansX = new Vec3(0.7071067811865476, 0, 0.7071067811865475);
        Vec3 leansZ = new Vec3(0.7071067811865475, 0, 0.7071067811865476);
        assertEquals(CrystalClusterSubmitter.tilePieces(face, leansX).getFirst().getFirst().u(),
                CrystalClusterSubmitter.tilePieces(face, leansZ).getFirst().getFirst().u(), EPSILON,
                "rounding in the normal should not swap the plane the face reads, or it flickers as it grows");
    }

    @Test
    void everyPieceOfAGrownCrystalStaysOnTheSprite() {
        for (CrystalCluster.Prism prism : CrystalCluster.prisms(1.0)) {
            for (Vec3[] face : CrystalClusterSubmitter.prismFaces(prism)) {
                for (List<CrystalClusterSubmitter.TexelPoint> piece
                        : CrystalClusterSubmitter.tilePieces(face, CrystalClusterSubmitter.faceNormal(face))) {
                    for (CrystalClusterSubmitter.TexelPoint point : piece) {
                        assertTrue(point.u() >= 0 && point.u() <= SPRITE && point.v() >= 0 && point.v() <= SPRITE,
                                "texel " + point.u() + ", " + point.v() + " should lie on the sprite");
                    }
                }
            }
        }
    }

    /** A planar polygon's area, in square model pixels. */
    private static double areaOf(List<CrystalClusterSubmitter.TexelPoint> polygon) {
        Vec3 sum = Vec3.ZERO;
        for (int i = 0; i < polygon.size(); i++) {
            sum = sum.add(polygon.get(i).pos().cross(polygon.get((i + 1) % polygon.size()).pos()));
        }
        return sum.length() / 2;
    }
}
