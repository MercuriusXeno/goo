package com.mercuriusxeno.goo.ability.outsider;

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
class OutsiderRoomsTest {

    private static final int ROOM = 7;
    private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(ROOM);
    /** Three blocks east of the center: past the starting extent. */
    private static final int PAST_THE_EDGE = 3;

    @Nested
    class TheFold {

        @Test
        void aPlayerBelowTheCatchDepthFoldsBack() {
            Vec3 fallen = Vec3.atBottomCenterOf(CENTER).subtract(0, OutsiderRooms.CATCH_DEPTH + 1, 0);
            assertTrue(OutsiderRooms.foldsBack(fallen), "a player fallen below the catch depth should fold back");
        }

        @Test
        void aPlayerPastTheExtentAtFloorHeightStaysWhereTheyAre() {
            Vec3 past = Vec3.atBottomCenterOf(CENTER.east(PAST_THE_EDGE).above());
            assertFalse(OutsiderRooms.foldsBack(past),
                    "a player past the extent at floor height should stay where they are");
        }

    }

    @Nested
    class Growth {

        /** Past the first whole-block mark: the 5 by 5 and a share of the next ring. */
        private static final double PAST_THE_MARK = 2.6;
        /** Short of the first whole-block mark. */
        private static final double SHORT_OF_THE_MARK = 2.4;
        /** The cells of the 5 by 5 the first whole-block mark completes. */
        private static final int FIVE_BY_FIVE = 25;

        @Test
        void crossingAWholeBlockMarkSendsTheRoomsNewEdges() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            OutsiderRooms.announceGrowth(ROOM, SHORT_OF_THE_MARK, PAST_THE_MARK, sent::add);
            assertEquals(List.of(new RoomEdgesSyncPayload(RoomEdges.around(CENTER, FIVE_BY_FIVE).pairs())), sent);
        }

        @Test
        void feedingShortOfAWholeBlockMarkSendsNothing() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            OutsiderRooms.announceGrowth(ROOM, RoomExtent.LAID, SHORT_OF_THE_MARK, sent::add);
            assertEquals(List.of(), sent);
        }
    }
}
