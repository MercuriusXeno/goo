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
 * carry a walker to the opposite side, and the edges a growing platform sends
 * its player (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class OutsiderRoomsTest {

    private static final int ROOM = 7;
    private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(ROOM);
    /** Three blocks east of the center: past the starting 3 by 3's edge. */
    private static final int PAST_THE_EDGE = 3;

    @Nested
    class TheFold {

        @Test
        void aPlayerBelowTheCatchDepthFoldsBack() {
            Vec3 fallen = Vec3.atBottomCenterOf(CENTER).subtract(0, OutsiderRooms.CATCH_DEPTH + 1, 0);
            assertTrue(OutsiderRooms.foldsBack(fallen), "a player fallen below the catch depth should fold back");
        }

        @Test
        void aPlayerPastTheGrownCellsAtFloorHeightStaysWhereTheyAre() {
            Vec3 past = Vec3.atBottomCenterOf(CENTER.east(PAST_THE_EDGE).above());
            assertFalse(OutsiderRooms.foldsBack(past),
                    "a player past the grown cells at floor height should stay where they are");
        }

        @Test
        void aBlockPastTheGrownCellsIsStillRefused() {
            BlockPos past = CENTER.east(PAST_THE_EDGE);
            assertFalse(OutsiderRooms.overGrownCell(ROOM, PlatformSpiral.START_CELLS, past.getX(), past.getZ()),
                    "a column past the grown cells should still refuse a block");
        }
    }

    @Nested
    class Growth {

        @Test
        void growingACellSendsTheRoomsNewEdges() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            OutsiderRooms.announceGrowth(ROOM, PlatformSpiral.START_CELLS, PlatformSpiral.START_CELLS + 1, sent::add);
            assertEquals(List.of(new RoomEdgesSyncPayload(
                    RoomEdges.around(CENTER, PlatformSpiral.START_CELLS + 1).pairs())), sent);
        }

        @Test
        void feedingShortOfACellSendsNothing() {
            List<RoomEdgesSyncPayload> sent = new ArrayList<>();
            OutsiderRooms.announceGrowth(ROOM, PlatformSpiral.START_CELLS, PlatformSpiral.START_CELLS, sent::add);
            assertEquals(List.of(), sent);
        }
    }
}
