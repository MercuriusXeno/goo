package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystal's faces wrap the growing type's sprite around each prism and up it, two
 * texels per model pixel, the way around rounded to whole sprites, cut at the sprite's
 * edges so the texture repeats rather than clamps, and steady as the crystal grows
 * (decisions crystallizer-emits-chrysm, diagnose-then-fix-crystal-texture-seams); a
 * prism rounding into the materia marble keeps its hexagon unrounded and is a sphere,
 * its faces turned out, fully rounded (operator ruling).
 */
class CrystalClusterSubmitterTest {

    private static final double SPRITE = 16;
    private static final double EPSILON = 1e-9;
    private static final double TOLERANCE = 1e-6;
    private static final int BANDS = 8;
    private static final CrystalCluster.Prism MARBLE = new CrystalCluster.Prism(0, 0,
            2 * CrystalCluster.ORB_RADIUS, CrystalCluster.ORB_RADIUS, 1);

    private static CrystalClusterSubmitter.TexelPoint at(double x, double y, double u, double v) {
        return new CrystalClusterSubmitter.TexelPoint(new Vec3(x, y, 8), u, v);
    }

    @Test
    void aFullyRoundedPrismIsASphereOnTheSpotWithItsFacesTurnedOut() {
        double radius = CrystalCluster.ORB_RADIUS;
        Vec3 center = new Vec3(CrystalCluster.BASE_X, CrystalCluster.BASE_Y + radius, CrystalCluster.BASE_Z);
        for (Vec3[] face : CrystalClusterSubmitter.roundedFaces(MARBLE)) {
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
    void anUnroundedPrismMeshKeepsTheHexagonsCornersAndFlatSides() {
        CrystalCluster.Prism prism = new CrystalCluster.Prism(0, 0, 12, 2.5, 0);
        Vec3 axisAtBase = new Vec3(CrystalCluster.BASE_X, 0, CrystalCluster.BASE_Z);
        double sideMiddle = 2.5 * Math.cos(Math.PI / 6);
        for (Vec3[] face : CrystalClusterSubmitter.roundedFaces(prism)) {
            for (Vec3 corner : face) {
                double fromAxis = new Vec3(corner.x, 0, corner.z).distanceTo(axisAtBase);
                assertTrue(fromAxis <= 2.5 + 1e-6, "a corner outside the hexagon at " + corner);
                if (corner.y > CrystalCluster.BASE_Y + 1e-6 && corner.y < CrystalCluster.BASE_Y + 9 - 1e-6) {
                    assertTrue(fromAxis >= sideMiddle - 1e-6, "a shaft corner inside the hexagon's sides at " + corner);
                }
            }
        }
    }

    @Test
    void aSideReadsTwoTexelsAPixelAcrossAndUpWhenItsRimIsWholeSprites() {
        // Six sides of 8/3 pixels at two texels a pixel run exactly 32 texels around.
        CrystalCluster.Prism prism = new CrystalCluster.Prism(22.5, 45, 12, 8.0 / 3, 0);
        CrystalClusterSubmitter.TexelPoint[] side = CrystalClusterSubmitter.wrappedPrismFaces(prism).getFirst();
        CrystalClusterSubmitter.TexelPoint bottomLeft = side[0];
        CrystalClusterSubmitter.TexelPoint bottomRight = side[1];
        CrystalClusterSubmitter.TexelPoint topLeft = side[3];
        assertEquals(bottomLeft.pos().distanceTo(bottomRight.pos()) * 2, bottomRight.u() - bottomLeft.u(), EPSILON,
                "u runs two texels a pixel around the column");
        assertEquals(bottomLeft.pos().distanceTo(topLeft.pos()) * 2, bottomLeft.v() - topLeft.v(), EPSILON,
                "v runs two texels a pixel up the column, the sprite's top toward the tip");
    }

    @Test
    void aFaceAcrossASpriteEdgeIsCutNotClamped() {
        CrystalClusterSubmitter.TexelPoint[] face = {at(6, 20, 12, 8), at(10, 20, 20, 8), at(10, 24, 20, 0),
            at(6, 24, 12, 0)};
        List<List<CrystalClusterSubmitter.TexelPoint>> pieces = CrystalClusterSubmitter.tilePieces(face);
        assertEquals(2, pieces.size(), "u 12 to 20 crosses the edge at 16");
        double area = 0;
        for (List<CrystalClusterSubmitter.TexelPoint> piece : pieces) {
            for (CrystalClusterSubmitter.TexelPoint point : piece) {
                double wrapped = point.pos().x * 2 % SPRITE;
                assertTrue(Math.abs(point.u() - wrapped) < EPSILON || Math.abs(point.u() - SPRITE) < EPSILON
                                && wrapped < EPSILON, "u " + point.u() + " should repeat the face's texel " + wrapped);
            }
            area += areaOf(piece);
        }
        assertEquals(4 * 4, area, EPSILON, "the pieces together cover the face");
    }

    @Test
    void neighboringSidesReadDifferentColumnsOfTheSprite() {
        List<CrystalClusterSubmitter.TexelPoint[]> faces =
                CrystalClusterSubmitter.wrappedPrismFaces(new CrystalCluster.Prism(0, 0, 12, 2.5, 0));
        double first = CrystalClusterSubmitter.tilePieces(faces.get(0)).getFirst().getFirst().u();
        double second = CrystalClusterSubmitter.tilePieces(faces.get(2)).getFirst().getFirst().u();
        assertNotEquals(first, second, EPSILON, "two sides should not show the same sliver");
    }

    @Test
    void aCrystalGrowingByAHairKeepsItsTexels() {
        List<CrystalCluster.Prism> now = CrystalCluster.prisms(0.6);
        List<CrystalCluster.Prism> next = CrystalCluster.prisms(0.6 + 1e-9);
        assertEquals(now.size(), next.size());
        for (int p = 0; p < now.size(); p++) {
            List<CrystalClusterSubmitter.TexelPoint[]> before = CrystalClusterSubmitter.wrappedFaces(now.get(p));
            List<CrystalClusterSubmitter.TexelPoint[]> after = CrystalClusterSubmitter.wrappedFaces(next.get(p));
            for (int f = 0; f < before.size(); f++) {
                for (int c = 0; c < before.get(f).length; c++) {
                    assertEquals(before.get(f)[c].u(), after.get(f)[c].u(), TOLERANCE, "u flickers as it grows");
                    assertEquals(before.get(f)[c].v(), after.get(f)[c].v(), TOLERANCE, "v flickers as it grows");
                }
            }
        }
    }

    @Test
    void aColumnsPoleCornersTakeTheirNeighborsAngleSoThePoleFacesMeet() {
        double circumference = 48;
        Vec3 center = new Vec3(CrystalCluster.BASE_X, CrystalCluster.BASE_Y, CrystalCluster.BASE_Z);
        Vec3 east = center.add(4, 0, 0);
        Vec3 north = center.add(0, 0, -4);
        Vec3 west = center.add(-4, 0, 0);
        CrystalClusterSubmitter.TexelPoint[] first =
                CrystalClusterSubmitter.wrapAroundColumn(new Vec3[] {north, east, center, center}, circumference);
        CrystalClusterSubmitter.TexelPoint[] second =
                CrystalClusterSubmitter.wrapAroundColumn(new Vec3[] {west, north, center, center}, circumference);
        assertEquals(first[0].u(), first[3].u(), EPSILON, "the pole after the face's last corner takes its angle");
        assertEquals(second[1].u(), second[2].u(), EPSILON, "the pole after a corner takes that corner's angle");
        assertEquals(first[0].u(), second[1].u(), EPSILON, "the faces meet on the edge from north to the pole");
    }

    @Nested
    class Wrapping {

        @ParameterizedTest
        @ValueSource(doubles = {0, 0.5, 1})
        void neighboringFacesCarryOneTexelAtEachCornerOfTheEdgeTheyShare(double rounding) {
            for (CrystalCluster.Prism prism : List.of(new CrystalCluster.Prism(22.5, 45, 10, 2.2, rounding),
                    new CrystalCluster.Prism(0, 0, 12, 2.5, rounding))) {
                assertSharedEdgesAgree(CrystalClusterSubmitter.wrappedRoundedFaces(prism));
                if (rounding == 0) {
                    assertSharedEdgesAgree(CrystalClusterSubmitter.wrappedPrismFaces(prism));
                }
            }
            assertSharedEdgesAgree(CrystalClusterSubmitter.wrappedRoundedFaces(MARBLE));
        }

        @Test
        void noTexelOfAGrownCrystalOrTheMarbleIsClampedOntoTheSprite() {
            List<CrystalClusterSubmitter.TexelPoint[]> faces =
                    new ArrayList<>(CrystalClusterSubmitter.wrappedFaces(MARBLE));
            for (CrystalCluster.Prism prism : CrystalCluster.prisms(1.0)) {
                faces.addAll(CrystalClusterSubmitter.wrappedFaces(prism));
            }
            for (CrystalClusterSubmitter.TexelPoint[] face : faces) {
                List<List<CrystalClusterSubmitter.TexelPoint>> whole = CrystalClusterSubmitter.cutAtSpriteEdges(face);
                List<List<CrystalClusterSubmitter.TexelPoint>> local = CrystalClusterSubmitter.tilePieces(face);
                assertEquals(whole.size(), local.size());
                for (int p = 0; p < whole.size(); p++) {
                    for (int i = 0; i < whole.get(p).size(); i++) {
                        CrystalClusterSubmitter.TexelPoint before = whole.get(p).get(i);
                        CrystalClusterSubmitter.TexelPoint after = local.get(p).get(i);
                        assertWholeSpritesApart(before.u(), after.u(), "u at " + before.pos());
                        assertWholeSpritesApart(before.v(), after.v(), "v at " + before.pos());
                        assertTrue(after.u() >= -TOLERANCE && after.u() <= SPRITE + TOLERANCE
                                && after.v() >= -TOLERANCE && after.v() <= SPRITE + TOLERANCE,
                                "texel " + after.u() + ", " + after.v() + " off the sprite");
                    }
                }
            }
        }

        @ParameterizedTest
        @ValueSource(doubles = {0, 1})
        void theWrapMeetsItselfWhereItClosesAroundTheColumn(double rounding) {
            List<CrystalClusterSubmitter.TexelPoint[]> faces = CrystalClusterSubmitter.wrappedRoundedFaces(
                    new CrystalCluster.Prism(22.5, 45, 10, 2.2, rounding));
            int slices = faces.size() / BANDS;
            for (int band = 0; band < BANDS; band++) {
                CrystalClusterSubmitter.TexelPoint[] first = faces.get(band * slices);
                CrystalClusterSubmitter.TexelPoint[] last = faces.get(band * slices + slices - 1);
                assertTrue(sharedEdges(first, last).size() > 0, "the first and last slices of band " + band
                        + " should share the closing edge");
                assertSharedEdgesAgree(List.of(first, last));
            }
        }

        private void assertSharedEdgesAgree(List<CrystalClusterSubmitter.TexelPoint[]> faces) {
            for (int a = 0; a < faces.size(); a++) {
                for (int b = a + 1; b < faces.size(); b++) {
                    for (Vec3[] edge : sharedEdges(faces.get(a), faces.get(b))) {
                        for (Vec3 corner : edge) {
                            List<double[]> onA = texelsAt(CrystalClusterSubmitter.tilePieces(faces.get(a)), corner);
                            List<double[]> onB = texelsAt(CrystalClusterSubmitter.tilePieces(faces.get(b)), corner);
                            assertTrue(agreeSomewhere(onA, onB), "faces " + a + " and " + b + " disagree at "
                                    + corner + ": " + describe(onA) + " against " + describe(onB));
                        }
                    }
                }
            }
        }
    }

    private static List<Vec3[]> sharedEdges(CrystalClusterSubmitter.TexelPoint[] first,
                                            CrystalClusterSubmitter.TexelPoint[] second) {
        List<Vec3[]> shared = new ArrayList<>();
        for (int i = 0; i < first.length; i++) {
            Vec3 from = first[i].pos();
            Vec3 to = first[(i + 1) % first.length].pos();
            if (from.distanceTo(to) < TOLERANCE) {
                continue;
            }
            for (int j = 0; j < second.length; j++) {
                Vec3 otherFrom = second[j].pos();
                Vec3 otherTo = second[(j + 1) % second.length].pos();
                if (from.distanceTo(otherTo) < TOLERANCE && to.distanceTo(otherFrom) < TOLERANCE
                        || from.distanceTo(otherFrom) < TOLERANCE && to.distanceTo(otherTo) < TOLERANCE) {
                    shared.add(new Vec3[] {from, to});
                }
            }
        }
        return shared;
    }

    private static List<double[]> texelsAt(List<List<CrystalClusterSubmitter.TexelPoint>> pieces, Vec3 corner) {
        List<double[]> texels = new ArrayList<>();
        for (List<CrystalClusterSubmitter.TexelPoint> piece : pieces) {
            for (CrystalClusterSubmitter.TexelPoint point : piece) {
                if (point.pos().distanceTo(corner) < TOLERANCE) {
                    texels.add(new double[] {point.u(), point.v()});
                }
            }
        }
        return texels;
    }

    private static boolean agreeSomewhere(List<double[]> onA, List<double[]> onB) {
        for (double[] a : onA) {
            for (double[] b : onB) {
                if (offSprite(a[0] - b[0]) < TOLERANCE && offSprite(a[1] - b[1]) < TOLERANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String describe(List<double[]> texels) {
        StringBuilder text = new StringBuilder();
        for (double[] texel : texels) {
            text.append('(').append(texel[0]).append(", ").append(texel[1]).append(')');
        }
        return text.toString();
    }

    private static void assertWholeSpritesApart(double whole, double local, String what) {
        assertTrue(offSprite(whole - local) < TOLERANCE, what + ": " + whole + " clamped to " + local);
    }

    /** How far a texel difference sits from the nearest whole number of sprites. */
    private static double offSprite(double difference) {
        double rest = ((difference % SPRITE) + SPRITE) % SPRITE;
        return Math.min(rest, SPRITE - rest);
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
