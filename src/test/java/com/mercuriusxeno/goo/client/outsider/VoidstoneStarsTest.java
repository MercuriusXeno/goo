package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how densely a voidstone draws its stars: a block wholly inside the
 * room's boundary draws full stars, the block the boundary cuts, the one the
 * server's build test refuses, a sparser starfield, and a block gains its full
 * stars on the edge update that clears its far face; and the floor columns a
 * room lays starred faces on, whose cut ring and the neighbouring copy's
 * cover the strip between their full squares
 * (decisions boundary-cut-voidstone-draws-without-its-stars and room-edge-draws-a-sparser-starfield).
 */
class VoidstoneStarsTest {

    private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(6);
    /** An extent ending three tenths of the way into the blocks two out. */
    private static final double PARTWAY = 2.3;
    /** The same room grown past the far face of the blocks two out. */
    private static final double CLEARED = 2.6;

    @Nested
    class ByWhereTheColumnStands {

        @AfterEach
        void leaveTheRoom() {
            ClientRoomEdges.take(new RoomEdgesSyncPayload(List.of()));
            GatePassage.setClientPairs(List.of());
        }

        @ParameterizedTest(name = "toward ({0}, {1})")
        @CsvSource({"1, 0", "-1, 0", "0, 1", "0, -1"})
        void theCutBlockDrawsSparseStarsAndItsInnerNeighbourFullStars(int east, int south) {
            standIn(PARTWAY);

            assertEquals(VoidstoneStars.Density.FULL, densityAt(east, south),
                    "the block wholly inside should draw full stars");
            assertEquals(VoidstoneStars.Density.SPARSE, densityAt(2 * east, 2 * south),
                    "the block the boundary cuts should draw sparse stars");
        }

        @ParameterizedTest(name = "toward ({0}, {1})")
        @CsvSource({"1, 0", "-1, 0", "0, 1", "0, -1"})
        void fullStarsFollowTheServersBuildTestAcrossTheBoundary(int east, int south) {
            standIn(PARTWAY);

            for (int out = 0; out <= 2; out++) {
                assertEquals(RoomExtent.holdsWholeBlock(PARTWAY, out * east, out * south),
                        densityAt(out * east, out * south) == VoidstoneStars.Density.FULL,
                        "the block " + out + " out should draw full stars where it builds");
            }
        }

        @Test
        void aBlockGainsItsFullStarsOnTheUpdateThatClearsItsFarFace() {
            standIn(PARTWAY);
            assertEquals(VoidstoneStars.Density.SPARSE, densityAt(2, 0),
                    "the block two out should draw sparse stars while the boundary cuts it");

            standIn(CLEARED);
            assertEquals(VoidstoneStars.Density.FULL, densityAt(2, 0),
                    "the block two out should draw full stars once the boundary clears it");
        }

        private static void standIn(double extent) {
            ClientRoomEdges.take(new RoomEdgesSyncPayload(RoomEdges.around(CENTER, extent).pairs()));
        }

        private static VoidstoneStars.Density densityAt(int east, int south) {
            return VoidstoneStars.density(CENTER.getX() + east, CENTER.getZ() + south);
        }
    }

    @Nested
    class TheFloorColumnsARoomLays {

        private static final double REACH = 64;

        @ParameterizedTest(name = "extent {0}")
        @ValueSource(doubles = {2.3, 2.9})
        void theCutRingIsListedSparseAndEveryRingInsideItFull(double extent) {
            RoomExtent.Bounds bounds = new RoomExtent.Bounds(CENTER.getX(), CENTER.getZ(), extent);
            int cutRing = RoomExtent.wholeRings(extent) + 1;
            List<VoidstoneStars.Column> columns = VoidstoneStars.floorColumns(bounds);

            assertEquals((2 * cutRing + 1) * (2 * cutRing + 1), columns.size(),
                    "every column out to the cut ring should be listed");
            for (VoidstoneStars.Column column : columns) {
                int ring = Math.max(Math.abs(column.x() - CENTER.getX()), Math.abs(column.z() - CENTER.getZ()));
                assertEquals(ring == cutRing ? VoidstoneStars.Density.SPARSE : VoidstoneStars.Density.FULL,
                        column.density(), "ring " + ring + " at extent " + extent);
            }
        }

        @ParameterizedTest(name = "extent {0}")
        @ValueSource(doubles = {2.3, 2.9})
        void theRoomsCutRingAndTheEastCopysCoverTheStripBetweenTheirFullSquares(double extent) {
            assertStripCovered(extent, true);
        }

        @ParameterizedTest(name = "extent {0}")
        @ValueSource(doubles = {2.3, 2.9})
        void theRoomsCutRingAndTheSouthCopysCoverTheStripBetweenTheirFullSquares(double extent) {
            assertStripCovered(extent, false);
        }

        /**
         * Along the row through the room's center, the room's sparse spans and
         * the neighbouring copy's, each clipped at the boundary planes and the
         * copy's moved by its offset, cover the floor from the room's full
         * square to the copy's.
         */
        private static void assertStripCovered(double extent, boolean east) {
            RoomExtent.Bounds bounds = new RoomExtent.Bounds(CENTER.getX(), CENTER.getZ(), extent);
            RoomExtent.Planes planes = RoomExtent.planesAround(CENTER, extent);
            double low = east ? planes.west() : planes.north();
            double high = east ? planes.east() : planes.south();
            RoomCopies.Offset copy = RoomCopies.offsets(bounds, REACH).stream()
                    .filter(offset -> east ? offset.south() == 0 && offset.east() > 0
                            : offset.east() == 0 && offset.south() > 0)
                    .min(Comparator.comparingDouble(offset -> offset.east() + offset.south())).orElseThrow();
            double shift = east ? copy.east() : copy.south();
            List<VoidstoneStars.Column> row = VoidstoneStars.floorColumns(bounds).stream()
                    .filter(column -> east ? column.z() == CENTER.getZ() : column.x() == CENTER.getX()).toList();
            double fullEnd = -Double.MAX_VALUE;
            double fullStart = Double.MAX_VALUE;
            List<double[]> sparse = new ArrayList<>();
            for (VoidstoneStars.Column column : row) {
                int at = east ? column.x() : column.z();
                if (column.density() == VoidstoneStars.Density.FULL) {
                    fullEnd = Math.max(fullEnd, at + 1);
                    fullStart = Math.min(fullStart, at + shift);
                } else {
                    double from = Math.max(at, low);
                    double to = Math.min(at + 1, high);
                    sparse.add(new double[] {from, to});
                    sparse.add(new double[] {from + shift, to + shift});
                }
            }
            assertTrue(fullStart > fullEnd, "a strip should stand between the full squares at extent " + extent);
            sparse.sort(Comparator.comparingDouble(span -> span[0]));
            double covered = fullEnd;
            for (double[] span : sparse) {
                if (span[1] <= fullEnd || span[0] >= fullStart) {
                    continue;
                }
                assertTrue(span[0] <= covered + 1.0E-9, "the strip shows plain dark from " + covered + " to "
                        + span[0] + " at extent " + extent);
                covered = Math.max(covered, span[1]);
            }
            assertTrue(covered >= fullStart - 1.0E-9, "the strip shows plain dark from " + covered + " to "
                    + fullStart + " at extent " + extent);
        }
    }
}
