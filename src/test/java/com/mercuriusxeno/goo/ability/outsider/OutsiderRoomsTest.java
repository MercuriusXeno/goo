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

        /** A fifth of a block past the starting extent, short of any whole-block mark. */
        private static final double A_FRACTION_ON = 1.7;
        private static final double EXACT = 1.0E-9;

        @Test
        void feedingAFractionOfABlockSendsTheNewPlanesOnce() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            OutsiderRooms.announceGrowth(ROOM, RoomExtent.LAID, A_FRACTION_ON, sent::add);

            assertEquals(List.of(new RoomEdgesSyncPayload(RoomEdges.around(CENTER, A_FRACTION_ON).pairs())), sent);
            assertEquals(CENTER.getX() + 0.5 + A_FRACTION_ON, sent.getFirst().edges().getFirst().one().center().x(),
                    EXACT, "the east plane sent");
        }

        @Test
        void aFeedLeavingTheExtentWhereItStoodSendsNothing() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            OutsiderRooms.announceGrowth(ROOM, A_FRACTION_ON, A_FRACTION_ON, sent::add);
            assertEquals(List.of(), sent);
        }
    }
}
