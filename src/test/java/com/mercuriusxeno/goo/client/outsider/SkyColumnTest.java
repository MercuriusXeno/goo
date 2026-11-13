package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the sky column's shape and place: a square of the doorway's
 * footprint rising from the doorway's top straight up past any far plane,
 * and every vertex drawn along its own line of sight inside the farthest
 * drawn depth
 * (decision sky-funnel-points-down-to-the-doorway-from-any-distance).
 */
class SkyColumnTest {

    private static final double EXACT = 1.0E-6;
    private static final RoomExtent.Bounds NEAR_ROOM = new RoomExtent.Bounds(0, 0, RoomExtent.LAID);
    private static final RoomExtent.Bounds FAR_ROOM = new RoomExtent.Bounds(1_000_000, -2_000_000, 6.5);
    private static final double HALF_BLOCK = 0.5;
    /** The vanilla far plane at sixteen chunks: sixteen chunks of blocks, four times over. */
    private static final double FARTHEST = 1024;
    /** The vanilla far plane at thirty-two chunks, the farthest render distance. */
    private static final double FARTHEST_ANY_RENDER_DISTANCE = 2048;
    private static final double TEN_TIMES = 10;
    private static final double A_THOUSAND_TIMES = 1000;

    @Nested
    class Shape {

        @Test
        void everyRingIsASquareOfTheDoorwaysFootprint() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, FAR_ROOM)) {
                for (List<Vec3> ring : SkyColumn.rings(bounds)) {
                    for (Vec3 vertex : ring) {
                        assertEquals(HALF_BLOCK, Math.abs(eastOf(bounds, vertex)), EXACT, "the door's half width");
                        assertEquals(HALF_BLOCK, Math.abs(southOf(bounds, vertex)), EXACT, "the door's half width");
                        assertEquals(ring.getFirst().y(), vertex.y(), EXACT, "level");
                    }
                    assertEquals(4, ring.stream().map(vertex -> Math.signum(eastOf(bounds, vertex)) + ":"
                            + Math.signum(southOf(bounds, vertex))).distinct().count(), "all four corners");
                }
            }
        }

        @Test
        void theColumnRisesPastAnyFarPlane() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, FAR_ROOM)) {
                List<List<Vec3>> rings = SkyColumn.rings(bounds);
                for (int ring = 1; ring < rings.size(); ring++) {
                    assertTrue(rings.get(ring).getFirst().y() > rings.get(ring - 1).getFirst().y(),
                            "each ring higher than the one below");
                }
                assertTrue(rings.getLast().getFirst().y() - SkyColumn.spoutHeight() > FARTHEST_ANY_RENDER_DISTANCE,
                        "the top ring past the far plane at thirty-two chunks");
            }
        }

        private static double eastOf(RoomExtent.Bounds bounds, Vec3 vertex) {
            return vertex.x() - (bounds.centerX() + HALF_BLOCK);
        }

        private static double southOf(RoomExtent.Bounds bounds, Vec3 vertex) {
            return vertex.z() - (bounds.centerZ() + HALF_BLOCK);
        }
    }

    @Nested
    class Place {

        @Test
        void everyRingCentersOnTheRoomsCenterBlock() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, FAR_ROOM)) {
                List<List<Vec3>> rings = SkyColumn.rings(bounds);
                assertFalse(rings.isEmpty(), "the column stands rings");
                for (List<Vec3> ring : rings) {
                    double x = ring.stream().mapToDouble(Vec3::x).average().orElseThrow();
                    double z = ring.stream().mapToDouble(Vec3::z).average().orElseThrow();
                    assertEquals(bounds.centerX() + HALF_BLOCK, x, EXACT, "centered east-west on the center block");
                    assertEquals(bounds.centerZ() + HALF_BLOCK, z, EXACT, "centered north-south on the center block");
                }
            }
        }

        @Test
        void theLowestRingSitsOnTheTopOfTheDoorwaysUpperCell() {
            BlockPos upperCell = OutsiderRooms.doorOf(0).above();
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, FAR_ROOM)) {
                List<List<Vec3>> rings = SkyColumn.rings(bounds);
                double lowest = rings.stream().flatMap(List::stream).mapToDouble(Vec3::y).min().orElseThrow();
                assertEquals(upperCell.getY() + 1, lowest, EXACT, "on the top of the upper cell");
                for (Vec3 vertex : rings.getFirst()) {
                    assertEquals(upperCell.getY() + 1, vertex.y(), EXACT, "the first ring is the lowest");
                }
            }
        }
    }

    @Nested
    class Reach {

        @Test
        void aCameraInsideTheFarthestDepthSeesTheColumnAlongItsLinesOfSight() {
            assertEveryVertexInsideAlongItsLineOfSight(new Vec3(HALF_BLOCK + 20, 70, HALF_BLOCK - 30));
        }

        @Test
        void aCameraTenTimesTheFarthestDepthAwaySeesTheColumnAlongItsLinesOfSight() {
            assertEveryVertexInsideAlongItsLineOfSight(new Vec3(HALF_BLOCK + FARTHEST * TEN_TIMES, 70, HALF_BLOCK));
        }

        @Test
        void aCameraAThousandTimesTheFarthestDepthAwaySeesTheColumnAlongItsLinesOfSight() {
            assertEveryVertexInsideAlongItsLineOfSight(
                    new Vec3(HALF_BLOCK, 70, HALF_BLOCK - FARTHEST * A_THOUSAND_TIMES));
        }

        private void assertEveryVertexInsideAlongItsLineOfSight(Vec3 camera) {
            List<Vec3> vertices = SkyColumn.rings(NEAR_ROOM).stream().flatMap(List::stream).toList();
            assertFalse(vertices.isEmpty(), "the column stands vertices");
            for (Vec3 truePlace : vertices) {
                Vec3 toTrue = truePlace.subtract(camera);
                Vec3 placed = SkyColumn.fromCamera(camera, truePlace, FARTHEST);
                assertEquals(0, placed.normalize().cross(toTrue.normalize()).length(), EXACT,
                        "on the line of sight through the true place");
                assertTrue(placed.dot(toTrue) > 0, "on the true place's side of the camera");
                assertTrue(placed.length() < FARTHEST, "nearer the camera than the farthest drawn depth");
            }
        }
    }
}
