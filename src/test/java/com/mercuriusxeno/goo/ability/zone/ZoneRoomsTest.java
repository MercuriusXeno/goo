package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pocket's fold, which reads the fall alone now the room's edges
 * carry a walker to the opposite side, and the edges a growing room sends
 * its player (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class ZoneRoomsTest {

    private static final int ROOM = 7;
    private static final BlockPos CENTER = ZoneRooms.voidstoneOf(ROOM);
    /** Five blocks east of the center: past the starting extent. */
    private static final int PAST_THE_EDGE = 5;

    @Nested
    class TheFold {

        @Test
        void aPlayerBelowTheCatchDepthFoldsBack() {
            Vec3 fallen = Vec3.atBottomCenterOf(CENTER).subtract(0, ZoneRooms.CATCH_DEPTH + 1, 0);
            assertTrue(ZoneRooms.foldsBack(fallen), "a player fallen below the catch depth should fold back");
        }

        @Test
        void aPlayerPastTheExtentAtFloorHeightStaysWhereTheyAre() {
            Vec3 past = Vec3.atBottomCenterOf(CENTER.east(PAST_THE_EDGE).above());
            assertFalse(ZoneRooms.foldsBack(past),
                    "a player past the extent at floor height should stay where they are");
        }

    }

    @Nested
    class Growth {

        /** A fifth of a block past the starting extent, short of any whole-block mark. */
        private static final double A_FRACTION_ON = 4.7;
        private static final double EXACT = 1.0E-9;

        @Test
        void feedingAFractionOfABlockSendsTheNewPlanesOnce() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            ZoneRooms.announceGrowth(ROOM, RoomExtent.LAID, A_FRACTION_ON, sent::add);

            assertEquals(List.of(new RoomEdgesSyncPayload(RoomEdges.around(CENTER, A_FRACTION_ON).pairs())), sent);
            assertEquals(CENTER.getX() + 0.5 + A_FRACTION_ON, sent.getFirst().edges().getFirst().one().center().x(),
                    EXACT, "the east plane sent");
        }

        @Test
        void aFeedLeavingTheExtentWhereItStoodSendsNothing() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            ZoneRooms.announceGrowth(ROOM, A_FRACTION_ON, A_FRACTION_ON, sent::add);
            assertEquals(List.of(), sent);
        }
    }

    /**
     * Each full block the room gains is felt: one wub and a line of rising
     * void motes along each wall at the new boundary, and a feed short of a
     * whole-block mark shows neither
     * (decision full-block-growth-wubs-and-raises-void-motes).
     */
    @Nested
    class FullBlockGrowth {

        private static final int BASE = 10;
        private static final int PER_DISTANCE = 1;
        /** Just short of the first whole-block mark at 5.5. */
        private static final double SHORT_OF_THE_MARK = 5.45;
        private static final double EXACT = 1.0E-9;
        private static final double FLOOR_TOP = ZoneRooms.ROOM_FLOOR_Y + 1;

        private final List<Boolean> wubs = new ArrayList<>();
        private final List<Vec3[]> lines = new ArrayList<>();
        private final ZoneRooms.GrowthCues recorded = new ZoneRooms.GrowthCues() {
            @Override
            public void wub() {
                wubs.add(true);
            }

            @Override
            public void moteLine(Vec3 from, Vec3 to) {
                lines.add(new Vec3[] {from, to});
            }
        };

        @Test
        void aFeedAcrossAWholeBlockMarkWubsOnceAndRaisesAMoteLineAlongEachWall() {
            long ringFive = PlatformSpiral.ringCost(5, BASE, PER_DISTANCE);
            double after = RoomExtent.grown(SHORT_OF_THE_MARK, ringFive / 5, BASE, PER_DISTANCE);
            ZoneRooms.cueGrowth(ROOM, SHORT_OF_THE_MARK, after, recorded);

            assertEquals(1, wubs.size(), "one wub");
            RoomExtent.Planes planes = RoomExtent.planesAround(CENTER, after);
            List<Vec3[]> walls = List.of(
                    new Vec3[] {new Vec3(planes.east(), FLOOR_TOP, planes.north()),
                        new Vec3(planes.east(), FLOOR_TOP, planes.south())},
                    new Vec3[] {new Vec3(planes.west(), FLOOR_TOP, planes.north()),
                        new Vec3(planes.west(), FLOOR_TOP, planes.south())},
                    new Vec3[] {new Vec3(planes.west(), FLOOR_TOP, planes.south()),
                        new Vec3(planes.east(), FLOOR_TOP, planes.south())},
                    new Vec3[] {new Vec3(planes.west(), FLOOR_TOP, planes.north()),
                        new Vec3(planes.east(), FLOOR_TOP, planes.north())});
            assertEquals(walls.size(), lines.size(), "a line per wall");
            for (int wall = 0; wall < walls.size(); wall++) {
                assertEquals(0, walls.get(wall)[0].distanceTo(lines.get(wall)[0]), EXACT, "wall " + wall + " start");
                assertEquals(0, walls.get(wall)[1].distanceTo(lines.get(wall)[1]), EXACT, "wall " + wall + " end");
            }
        }

        @Test
        void aFeedCrossingNoMarkWubsNotAndRaisesNoWallMotes() {
            ZoneRooms.cueGrowth(ROOM, RoomExtent.LAID, 4.9, recorded);

            assertEquals(List.of(), wubs, "no wub");
            assertEquals(0, lines.size(), "no wall motes");
        }
    }
}
