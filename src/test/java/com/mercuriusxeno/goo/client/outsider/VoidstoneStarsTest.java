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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which voidstone draws its stars: a block wholly inside the room's
 * boundary and the block the boundary cuts, the one the server's build test
 * refuses, both draw them and a block wholly beyond draws none, and a block
 * gains its stars on the edge update that reaches it; and the floor columns a
 * room lays starred faces on, whose cut ring and the neighbouring copy's
 * cover the strip between their whole squares
 * (decisions boundary-cut-voidstone-draws-without-its-stars and room-edge-draws-a-sparser-starfield).
 */
class VoidstoneStarsTest {

    private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(6);
    /** An extent ending three tenths of the way into the blocks two out. */
    private static final double PARTWAY = 2.3;
    /** The same room grown into the blocks three out. */
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
        void theCutBlockDrawsTheStarsItsInnerNeighbourDrawsAndTheBlockBeyondNone(int east, int south) {
            standIn(PARTWAY);

            assertTrue(starredAt(east, south), "the block wholly inside should draw its stars");
            assertTrue(starredAt(2 * east, 2 * south), "the block the boundary cuts should draw its stars");
            assertFalse(starredAt(3 * east, 3 * south), "the block wholly beyond should draw none");
        }

        @ParameterizedTest(name = "toward ({0}, {1})")
        @CsvSource({"1, 0", "-1, 0", "0, 1", "0, -1"})
        void theCutBlockIsTheBlockTheServersBuildTestRefuses(int east, int south) {
            standIn(PARTWAY);

            assertFalse(RoomExtent.holdsWholeBlock(PARTWAY, 2 * east, 2 * south),
                    "the server's build test should refuse the block two out");
            assertTrue(new RoomExtent.Bounds(CENTER.getX(), CENTER.getZ(), PARTWAY)
                    .cutsBlock(CENTER.getX() + 2 * east, CENTER.getZ() + 2 * south),
                    "the boundary should cut the block two out");
        }

        @Test
        void aBlockGainsItsStarsOnTheUpdateThatReachesIt() {
            standIn(PARTWAY);
            assertFalse(starredAt(3, 0), "the block three out should draw no stars while the boundary stops short");

            standIn(CLEARED);
            assertTrue(starredAt(3, 0), "the block three out should draw its stars once the boundary cuts it");
        }

        private static void standIn(double extent) {
            ClientRoomEdges.take(new RoomEdgesSyncPayload(RoomEdges.around(CENTER, extent).pairs()));
        }

        private static boolean starredAt(int east, int south) {
            return VoidstoneStars.starred(CENTER.getX() + east, CENTER.getZ() + south);
        }
    }

    @Nested
    class TheFloorColumnsARoomLays {

        private static final double REACH = 64;

        @ParameterizedTest(name = "extent {0}")
        @ValueSource(doubles = {2.3, 2.9})
        void everyRingOutToTheCutRingIsListed(double extent) {
            RoomExtent.Bounds bounds = new RoomExtent.Bounds(CENTER.getX(), CENTER.getZ(), extent);
            int cutRing = RoomExtent.wholeRings(extent) + 1;
            List<VoidstoneStars.Column> columns = VoidstoneStars.floorColumns(bounds);

            assertEquals((2 * cutRing + 1) * (2 * cutRing + 1), columns.size(),
                    "every column out to the cut ring should be listed");
            for (VoidstoneStars.Column column : columns) {
                int ring = Math.max(Math.abs(column.x() - CENTER.getX()), Math.abs(column.z() - CENTER.getZ()));
                assertEquals(ring == cutRing, bounds.cutsBlock(column.x(), column.z()),
                        "ring " + ring + " at extent " + extent + " should be cut only where it is the cut ring");
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
         * Along the row through the room's center, the room's cut spans and
         * the neighbouring copy's, each clipped at the boundary planes and the
         * copy's moved by its offset, cover the floor from the room's whole
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
            List<double[]> cut = new ArrayList<>();
            for (VoidstoneStars.Column column : row) {
                int at = east ? column.x() : column.z();
                if (bounds.holdsWholeBlock(column.x(), column.z())) {
                    fullEnd = Math.max(fullEnd, at + 1);
                    fullStart = Math.min(fullStart, at + shift);
                } else {
                    double from = Math.max(at, low);
                    double to = Math.min(at + 1, high);
                    cut.add(new double[] {from, to});
                    cut.add(new double[] {from + shift, to + shift});
                }
            }
            assertTrue(fullStart > fullEnd, "a strip should stand between the whole squares at extent " + extent);
            cut.sort(Comparator.comparingDouble(span -> span[0]));
            double covered = fullEnd;
            for (double[] span : cut) {
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
