package com.mercuriusxeno.goo.ability.zone;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where the server holds a room's far side loaded for a player: one
 * room width back across each edge they stand near, both and the corner where
 * they stand near two, and nowhere while they stand far from every edge
 * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class RoomFarSideTest {

    /** A room 400.4 blocks across, wider than any render distance, centered at 1000, 0. */
    private static final RoomExtent.Bounds WIDE = new RoomExtent.Bounds(1_000, 0, 200.2);
    private static final double WIDTH = 400.4;
    private static final double EAST_PLANE = 1_000.5 + 200.2;

    @Test
    void aPlayerNearTheEastEdgeHasTheWestSideHeld() {
        List<RoomFarSide.Spot> spots = RoomFarSide.across(WIDE, EAST_PLANE - 3, 10);

        assertEquals(1, spots.size());
        assertEquals(EAST_PLANE - 3 - WIDTH, spots.get(0).x(), 1.0E-9, "one room width back west");
        assertEquals(10, spots.get(0).z(), 1.0E-9);
    }

    @Test
    void aPlayerNearTheNorthwestCornerHasBothSidesAndTheCornerHeld() {
        List<RoomFarSide.Spot> spots = RoomFarSide.across(WIDE, 1_000.5 - 200.2 + 4, 0.5 - 200.2 + 6);

        assertEquals(3, spots.size(), "east across, south across and the corner across both");
        assertTrue(spots.stream().allMatch(spot -> spot.x() > 1_000.5 || spot.z() > 0.5),
                "each lands across the room, east or south");
    }

    @Test
    void aPlayerFarFromEveryEdgeHasNothingHeld() {
        assertTrue(RoomFarSide.across(WIDE, 1_000.5, 0.5).isEmpty());
    }
}
