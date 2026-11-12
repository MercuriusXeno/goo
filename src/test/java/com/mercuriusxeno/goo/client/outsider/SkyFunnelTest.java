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
 * Covers the sky funnel's shape and place: a horn whose radius grows with
 * the square of its height, its spout on the room's own doorway, and every
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

    @Nested
    class Profile {

        @Test
        void theWallsMeetInAPointOnTheDoorwaysTop() {
            BlockPos upperCell = OutsiderRooms.doorOf(0).above();
            List<Vec3> point = SkyFunnel.rings(NEAR_ROOM).getFirst();

            assertEquals(0, SkyFunnel.heightAt(0), EXACT, "the point stands on the doorway's top");
            for (Vec3 vertex : point) {
                assertEquals(NEAR_ROOM.centerX() + HALF_BLOCK, vertex.x(), EXACT, "at radius zero");
                assertEquals(NEAR_ROOM.centerZ() + HALF_BLOCK, vertex.z(), EXACT, "at radius zero");
                assertEquals(upperCell.getY() + 1, vertex.y(), EXACT, "on the top of the upper cell");
            }
        }

        @Test
        void theHeightIsADownwardParabolaOfTheRadiusLevelAtTheRim() {
            double step = SkyFunnel.RIM_RADIUS / SkyFunnel.BANDS;
            double firstGain = SkyFunnel.heightAt(step) - SkyFunnel.heightAt(0);
            double gainBelow = Double.MAX_VALUE;
            for (int band = 0; band < SkyFunnel.BANDS; band++) {
                double gain = SkyFunnel.heightAt((band + 1) * step) - SkyFunnel.heightAt(band * step);
                assertTrue(gain > 0, "each step stands higher than the one before");
                assertTrue(gain < gainBelow, "each step gains less height than the one before");
                gainBelow = gain;
            }
            assertTrue(gainBelow < firstGain / 10, "the last step before the rim is level");
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
