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
 * Covers the sky funnel's shape and place: a door-width pillar standing from
 * the floor round the room's own doorway, peeling out toward its ceiling to
 * cover the sky, and every
 * vertex drawn along its own line of sight inside the farthest drawn depth
 * (decision sky-funnel-points-down-to-the-doorway-from-any-distance).
 */
class SkyFunnelTest {

    private static final double EXACT = 1.0E-6;
    private static final RoomExtent.Bounds NEAR_ROOM = new RoomExtent.Bounds(0, 0, RoomExtent.LAID);
    private static final RoomExtent.Bounds FAR_ROOM = new RoomExtent.Bounds(1_000_000, -2_000_000, 6.5);
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
            assertEquals(HALF_BLOCK, SkyFunnel.radiusAt(0), EXACT, "the doorway's half width at the floor");
            assertTrue(SkyFunnel.radiusAt(SkyFunnel.CEILING * NINE_TENTHS) - HALF_BLOCK < QUARTER_BLOCK,
                    "within a quarter block of it at nine tenths of the ceiling");
        }

        @Test
        void thePillarPeelsOutToCoverTheSky() {
            List<Double> radii = SkyFunnel.rings(NEAR_ROOM).stream()
                    .map(ring -> Math.hypot(ring.getFirst().x() - HALF_BLOCK, ring.getFirst().z() - HALF_BLOCK))
                    .toList();
            for (int ring = 1; ring < radii.size(); ring++) {
                assertTrue(radii.get(ring) > radii.get(ring - 1), "each ring wider than the ring below");
            }
            assertTrue(radii.getLast() > FARTHEST, "the top ring reaches past a sixteen-chunk pass's far depth");
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
        void theLowestRingSitsOnTheFloorTheDoorwayStandsOn() {
            BlockPos lowerCell = OutsiderRooms.doorOf(0);
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, FAR_ROOM)) {
                List<List<Vec3>> rings = SkyFunnel.rings(bounds);
                double lowest = rings.stream().flatMap(List::stream).mapToDouble(Vec3::y).min().orElseThrow();
                assertEquals(lowerCell.getY(), lowest, EXACT, "at the foot of the doorway's lower cell");
                for (Vec3 vertex : rings.getFirst()) {
                    assertEquals(lowerCell.getY(), vertex.y(), EXACT, "the first ring is the lowest");
                }
            }
        }

        @Test
        void theWallEnclosesBothDoorwayCells() {
            double upperTop = OutsiderRooms.doorOf(0).above().getY() + 1;
            for (RoomExtent.Bounds bounds : List.of(NEAR_ROOM, FAR_ROOM)) {
                List<List<Vec3>> doorwayRings = SkyFunnel.rings(bounds).stream()
                        .filter(ring -> ring.getFirst().y() <= upperTop).toList();
                assertFalse(doorwayRings.isEmpty(), "rings stand round the doorway");
                for (List<Vec3> ring : doorwayRings) {
                    Vec3 first = ring.getFirst();
                    double radius = Math.hypot(first.x() - (bounds.centerX() + HALF_BLOCK),
                            first.z() - (bounds.centerZ() + HALF_BLOCK));
                    assertTrue(radius >= HALF_BLOCK - EXACT, "no narrower than the doorway's half width");
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
            List<Vec3> vertices = SkyFunnel.rings(NEAR_ROOM).stream().flatMap(List::stream).toList();
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
