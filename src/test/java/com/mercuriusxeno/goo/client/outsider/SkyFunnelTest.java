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
 * Covers the sky funnel's shape and place: a door-width square column rising
 * from the doorway's top, curving out ever faster until it meets the room's
 * edges at its ceiling, its top the room's rectangle, and every vertex drawn along
 * its own line of sight inside the farthest drawn depth
 * (decision sky-funnel-points-down-to-the-doorway-from-any-distance).
 */
class SkyFunnelTest {

    private static final double EXACT = 1.0E-6;
    private static final RoomExtent.Bounds NEAR_ROOM = new RoomExtent.Bounds(0, 0, RoomExtent.LAID);
    private static final RoomExtent.Bounds FAR_ROOM = new RoomExtent.Bounds(1_000_000, -2_000_000, 6.5);
    private static final RoomExtent.Bounds GROWN_ROOM = new RoomExtent.Bounds(40, -70, 300.5);
    private static final double HALF_BLOCK = 0.5;
    /** The vanilla far plane at sixteen chunks: sixteen chunks of blocks, four times over. */
    private static final double FARTHEST = 1024;
    private static final double TEN_TIMES = 10;
    private static final double A_THOUSAND_TIMES = 1000;
    private static final double QUARTER_BLOCK = 0.25;
    /** The ceiling's height, above goo:pocket's build height of 256. */
    private static final double CEILING_Y = 512;

    @Nested
    class Profile {

        @Test
        void everyRingIsASquareCenteredOnTheCenterBlock() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, GROWN_ROOM)) {
                for (List<Vec3> ring : SkyFunnel.rings(bounds)) {
                    double halfWidth = halfWidthOf(bounds, ring.getFirst());
                    for (Vec3 vertex : ring) {
                        assertEquals(halfWidth, halfWidthOf(bounds, vertex), EXACT, "on the ring's square");
                        assertEquals(ring.getFirst().y(), vertex.y(), EXACT, "level");
                    }
                    for (double side : List.of(-halfWidth, halfWidth)) {
                        assertTrue(ring.stream().anyMatch(vertex -> Math.abs(eastOf(bounds, vertex) - side) < EXACT
                                && Math.abs(southOf(bounds, vertex) - side) < EXACT), "the square's corners stand");
                    }
                }
            }
        }

        @Test
        void thePillarHoldsTheDoorsWidthUpToHalfItsHeight() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, GROWN_ROOM)) {
                List<List<Vec3>> rings = SkyFunnel.rings(bounds);
                assertEquals(HALF_BLOCK, halfWidthOf(bounds, rings.getFirst().getFirst()), EXACT,
                        "the first ring the doorway's half width");
                double halfHeight = (SkyFunnel.spoutHeight() + SkyFunnel.CEILING_Y) / 2;
                for (List<Vec3> ring : rings) {
                    if (ring.getFirst().y() <= halfHeight) {
                        assertTrue(halfWidthOf(bounds, ring.getFirst()) - HALF_BLOCK < QUARTER_BLOCK,
                                "within a quarter block of the door's half width up to half the height");
                    }
                }
            }
        }

        @Test
        void eachRingWidensMoreThanTheOneBelow() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, GROWN_ROOM)) {
                List<List<Vec3>> rings = SkyFunnel.rings(bounds);
                double widenedBelow = 0;
                for (int ring = 1; ring < rings.size(); ring++) {
                    double widened = halfWidthOf(bounds, rings.get(ring).getFirst())
                            - halfWidthOf(bounds, rings.get(ring - 1).getFirst());
                    assertTrue(widened > widenedBelow, "each ring widens more than the one below");
                    widenedBelow = widened;
                }
            }
        }

        @Test
        void theTopRingIsTheRoomsRectangleAtTheCeiling() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, GROWN_ROOM)) {
                RoomExtent.Planes planes = RoomExtent.planesAround(
                        new BlockPos(bounds.centerX(), 0, bounds.centerZ()), bounds.extent());
                List<List<Vec3>> rings = SkyFunnel.rings(bounds);
                for (Vec3 vertex : rings.stream().flatMap(List::stream).toList()) {
                    assertTrue(vertex.x() >= planes.west() - EXACT && vertex.x() <= planes.east() + EXACT
                            && vertex.z() >= planes.north() - EXACT && vertex.z() <= planes.south() + EXACT,
                            "inside the room's boundary planes");
                }
                for (Vec3 vertex : rings.getLast()) {
                    assertEquals(CEILING_Y, vertex.y(), EXACT, "at the ceiling, y 512");
                    assertEquals(bounds.extent(), halfWidthOf(bounds, vertex), EXACT, "on the room's edges");
                }
            }
        }

        private static double eastOf(RoomExtent.Bounds bounds, Vec3 vertex) {
            return vertex.x() - (bounds.centerX() + HALF_BLOCK);
        }

        private static double southOf(RoomExtent.Bounds bounds, Vec3 vertex) {
            return vertex.z() - (bounds.centerZ() + HALF_BLOCK);
        }

        private static double halfWidthOf(RoomExtent.Bounds bounds, Vec3 vertex) {
            return Math.max(Math.abs(eastOf(bounds, vertex)), Math.abs(southOf(bounds, vertex)));
        }
    }

    @Nested
    class Place {

        @Test
        void everyRingCentersOnTheRoomsCenterBlock() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, FAR_ROOM)) {
                List<List<Vec3>> rings = SkyFunnel.rings(bounds);
                assertFalse(rings.isEmpty(), "the funnel stands rings");
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
                List<List<Vec3>> rings = SkyFunnel.rings(bounds);
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
        void aCameraInsideTheFarthestDepthSeesTheFunnelAlongItsLinesOfSight() {
            assertEveryVertexInsideAlongItsLineOfSight(new Vec3(HALF_BLOCK + 20, 70, HALF_BLOCK - 30));
        }

        @Test
        void aCameraTenTimesTheFarthestDepthAwaySeesTheFunnelAlongItsLinesOfSight() {
            assertEveryVertexInsideAlongItsLineOfSight(new Vec3(HALF_BLOCK + FARTHEST * TEN_TIMES, 70, HALF_BLOCK));
        }

        @Test
        void aCameraAThousandTimesTheFarthestDepthAwaySeesTheFunnelAlongItsLinesOfSight() {
            assertEveryVertexInsideAlongItsLineOfSight(
                    new Vec3(HALF_BLOCK, 70, HALF_BLOCK - FARTHEST * A_THOUSAND_TIMES));
        }

        private void assertEveryVertexInsideAlongItsLineOfSight(Vec3 camera) {
            List<Vec3> vertices = SkyFunnel.rings(GROWN_ROOM).stream().flatMap(List::stream).toList();
            assertFalse(vertices.isEmpty(), "the funnel stands vertices");
            for (Vec3 truePlace : vertices) {
                Vec3 toTrue = truePlace.subtract(camera);
                Vec3 placed = SkyFunnel.fromCamera(camera, truePlace, FARTHEST);
                assertEquals(0, placed.normalize().cross(toTrue.normalize()).length(), EXACT,
                        "on the line of sight through the true place");
                assertTrue(placed.dot(toTrue) > 0, "on the true place's side of the camera");
                assertTrue(placed.length() < FARTHEST, "nearer the camera than the farthest drawn depth");
            }
        }
    }
}
