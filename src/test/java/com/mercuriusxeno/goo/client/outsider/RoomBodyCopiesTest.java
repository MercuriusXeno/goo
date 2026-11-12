package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where the bodies a room holds draw in its copies: one place a copy,
 * the body's drawn place moved by that copy's offset, and for the local
 * player in first person every copy and never its own place
 * (decision room-tiles-to-render-distance-held-to-the-picture).
 */
class RoomBodyCopiesTest {

    /** A room partway grown, its width a fraction of a block. */
    private static final RoomExtent.Bounds ROOM = new RoomExtent.Bounds(1_000_000, 0, 2.3);
    private static final double REACH = 64;
    /** A body's drawn place inside the room, between two ticks. */
    private static final Vec3 BODY = new Vec3(1_000_001.25, 64.0, -0.75);

    @Test
    void aBodyInTheRoomStandsInIt() {
        assertTrue(RoomBodyCopies.standsIn(ROOM, BODY), "a body inside the boundary");
        assertFalse(RoomBodyCopies.standsIn(ROOM, BODY.add(5, 0, 0)), "a body past the east edge");
    }

    @Test
    void aBodyDrawsOnceInEachCopyAtItsPlaceMovedByTheOffset() {
        List<RoomCopies.Offset> offsets = RoomCopies.offsets(ROOM, REACH);

        List<Vec3> places = RoomBodyCopies.placesOf(BODY, offsets);

        assertEquals(offsets.size(), places.size(), "one place per copy");
        for (int copy = 0; copy < offsets.size(); copy++) {
            assertEquals(BODY.add(offsets.get(copy).east(), 0, offsets.get(copy).south()), places.get(copy),
                    "the place in copy " + copy);
        }
    }

    @Test
    void theLocalPlayerInFirstPersonDrawsInEveryCopyAndNeverAtItsOwnPlace() {
        List<RoomCopies.Offset> offsets = RoomCopies.offsets(ROOM, REACH);

        List<Vec3> places = RoomBodyCopies.placesOf(BODY, offsets);

        assertEquals(offsets.size(), places.size(), "a place in every copy");
        assertFalse(places.contains(BODY), "no place at the player's own, where first person draws no body");
    }
}
