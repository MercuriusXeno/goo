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
 * Covers the sky funnel's shape and place: a door-width pillar rising from
 * the doorway's top, peeling out toward its ceiling until it meets the
 * room's edges, its top the room's rectangle, and every vertex drawn along
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
    private static final double NINE_TENTHS = 0.9;
    private static final double QUARTER_BLOCK = 0.25;

    @Nested
    class Profile {

        @Test
        void thePillarHoldsTheDoorsWidthAlmostAllTheWayUp() {
            assertEquals(HALF_BLOCK, SkyFunnel.radiusAt(0), EXACT, "the doorway's half width at its top");
            assertTrue(SkyFunnel.radiusAt(SkyFunnel.CEILING * NINE_TENTHS) - HALF_BLOCK < QUARTER_BLOCK,
                    "within a quarter block of it at nine tenths of the ceiling");
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, GROWN_ROOM)) {
                for (Vec3 vertex : SkyFunnel.rings(bounds).getFirst()) {
                    assertEquals(HALF_BLOCK, radiusOf(bounds, vertex), EXACT, "the first ring the door's width");
                }
            }
        }

        @Test
        void eachRingStandsNoNarrowerThanTheOneBelow() {
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, GROWN_ROOM)) {
                List<List<Vec3>> rings = SkyFunnel.rings(bounds);
                double below = 0;
                for (List<Vec3> ring : rings) {
                    double widest = ring.stream().mapToDouble(vertex -> radiusOf(bounds, vertex)).max().orElseThrow();
                    assertTrue(widest >= below - EXACT, "no narrower than the ring below");
                    below = widest;
                }
            }
        }

        @Test
        void theFunnelStopsAtTheRoomsEdgesItsTopTheRoomsRectangle() {
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
                    boolean onAPlane = Math.abs(vertex.x() - planes.west()) < EXACT
                            || Math.abs(vertex.x() - planes.east()) < EXACT
                            || Math.abs(vertex.z() - planes.north()) < EXACT
                            || Math.abs(vertex.z() - planes.south()) < EXACT;
                    assertTrue(onAPlane, "the top ring on the room's edge");
                }
            }
        }

        private static double radiusOf(RoomExtent.Bounds bounds, Vec3 vertex) {
            return Math.hypot(vertex.x() - (bounds.centerX() + HALF_BLOCK), vertex.z() - (bounds.centerZ() + HALF_BLOCK));
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
