package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where the bodies a room holds draw across the tiled room: one draw a
 * copy, the body's drawn place moved by that copy's offset, beside the
 * frame's own draw at its place; and for the camera's own body in first
 * person, a draw in every copy and none at its own place
 * (decision room-tiles-to-render-distance-held-to-the-picture).
 */
class RoomBodyCopiesTest {

    /** A room partway grown, its width a fraction of a block. */
    private static final RoomExtent.Bounds ROOM = new RoomExtent.Bounds(1_000_000, 0, 2.3);
    private static final double REACH = 64;
    /** A body's drawn place inside the room, between two ticks. */
    private static final Vec3 BODY = new Vec3(1_000_001.25, 64.0, -0.75);
    private static final List<RoomCopies.Offset> OFFSETS = RoomCopies.offsets(ROOM, REACH);

    @Test
    void aBodyInTheRoomStandsInIt() {
        assertTrue(RoomBodyCopies.standsIn(ROOM, BODY), "a body inside the boundary");
        assertFalse(RoomBodyCopies.standsIn(ROOM, BODY.add(5, 0, 0)), "a body past the east edge");
    }

    @Test
    void aBodyDrawsAtItsOwnPlaceAndOnceInEachCopyAtItsPlaceMovedByTheOffset() {
        List<RoomBodyCopies.Draw> draws = RoomBodyCopies.drawsOf(BODY, OFFSETS, false, false);

        assertEquals(new RoomBodyCopies.Draw(BODY, true), draws.get(0), "the frame's own draw");
        assertCopiesAtEveryOffset(draws.subList(1, draws.size()));
    }

    @Test
    void theCameraBodyInFirstPersonDrawsInEveryCopyAndNoneAtItsOwnPlace() {
        List<RoomBodyCopies.Draw> draws = RoomBodyCopies.drawsOf(BODY, OFFSETS, true, false);

        assertCopiesAtEveryOffset(draws);
    }

    private static void assertCopiesAtEveryOffset(List<RoomBodyCopies.Draw> copies) {
        assertEquals(OFFSETS.size(), copies.size(), "one draw per copy and no other");
        for (int copy = 0; copy < OFFSETS.size(); copy++) {
            assertEquals(new RoomBodyCopies.Draw(BODY.add(OFFSETS.get(copy).east(), 0, OFFSETS.get(copy).south()),
                    false), copies.get(copy), "the draw in copy " + copy);
        }
    }
}
