package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where a room's copies stand: every whole width out to the reach,
 * corners included and the room's own place left out, and the reach read
 * from the render distance in blocks
 * (decision room-tiles-to-render-distance-held-to-the-picture).
 */
class RoomCopiesTest {

    /** A new room: 9 blocks across. */
    private static final RoomExtent.Bounds NEW_ROOM = new RoomExtent.Bounds(1_000_000, 0, RoomExtent.LAID);
    /** A grown room: 13 blocks across. */
    private static final RoomExtent.Bounds GROWN_ROOM = new RoomExtent.Bounds(1_000_000, 0, 6.5);
    private static final double FOUR_CHUNKS = 64;
    private static final double NEW_WIDTH = 9;
    /** The farthest whole width of a 9-block room within 64 blocks: 7 widths. */
    private static final double FARTHEST_WITHIN = 63;
    /** The next width out, past the reach. */
    private static final double PAST_THE_REACH = 72;

    @Nested
    class ByTheRoomsWidth {

        private final List<RoomCopies.Offset> offsets = RoomCopies.offsets(NEW_ROOM, FOUR_CHUNKS);

        @Test
        void aCopyStandsOneWidthEast() {
            assertTrue(offsets.contains(new RoomCopies.Offset(NEW_WIDTH, 0)), "one width east");
        }

        @Test
        void theCornerCopyStandsOneWidthEastAndOneNorth() {
            assertTrue(offsets.contains(new RoomCopies.Offset(NEW_WIDTH, -NEW_WIDTH)), "one east, one north");
        }

        @Test
        void theFarthestCopyWithinTheReachStands() {
            assertTrue(offsets.contains(new RoomCopies.Offset(FARTHEST_WITHIN, FARTHEST_WITHIN)),
                    "the far corner within the reach");
        }

        @Test
        void noCopyStandsOnTheRoomItselfOrPastTheReach() {
            assertFalse(offsets.contains(new RoomCopies.Offset(0, 0)), "the room's own place");
            assertFalse(offsets.contains(new RoomCopies.Offset(PAST_THE_REACH, 0)), "past the reach");
        }

        @Test
        void aWiderRoomShowsFewerCopiesAlongASide() {
            long newAlong = offsets.stream().filter(offset -> offset.south() == 0).count();
            long grownAlong = RoomCopies.offsets(GROWN_ROOM, FOUR_CHUNKS).stream()
                    .filter(offset -> offset.south() == 0).count();

            assertTrue(grownAlong < newAlong, "a 13-block room shows " + grownAlong + " along a side, a 9-block room "
                    + newAlong);
        }
    }

    @Nested
    class ByTheRenderDistance {

        private static final int TWELVE_CHUNKS = 12;
        private static final int FOUR_CHUNKS_SET = 4;

        @Test
        void twelveChunksReachOneHundredNinetyTwoBlocks() {
            assertEquals(192, RoomCopies.reachOf(TWELVE_CHUNKS));
        }

        @Test
        void fourChunksReachSixtyFourBlocks() {
            assertEquals(64, RoomCopies.reachOf(FOUR_CHUNKS_SET));
        }

        @Test
        void aLongerRenderDistanceShowsMoreCopies() {
            int far = RoomCopies.offsets(NEW_ROOM, RoomCopies.reachOf(TWELVE_CHUNKS)).size();
            int near = RoomCopies.offsets(NEW_ROOM, RoomCopies.reachOf(FOUR_CHUNKS_SET)).size();

            assertTrue(far > near, "12 chunks shows " + far + " copies, 4 chunks " + near);
        }

        /**
         * A 9-wide room repeats 21 widths each way within 192 blocks: 43 by 43 places less its own.
         * decision outsider-room-starts-nine-by-nine
         */
        @Test
        void aNewRoomAtTwelveChunksDrawsOneThousandEightHundredFortyEightCopies() {
            assertEquals(1_848, RoomCopies.offsets(NEW_ROOM, RoomCopies.reachOf(TWELVE_CHUNKS)).size());
        }
    }
}
