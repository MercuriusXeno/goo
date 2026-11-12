package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the draws a room's copies make: one instance for each copy the
 * camera sees, each carrying its copy's offset as the translation the
 * shaders read (decision room-tiles-to-render-distance-held-to-the-picture).
 */
class RoomCopyDrawsTest {

    /** A room partway grown, its width a fraction of a block. */
    private static final RoomExtent.Bounds ROOM = new RoomExtent.Bounds(1_000_000, -2_000_000, 2.3);
    private static final double FLOOR = 63;
    private static final double CEILING = 256;
    private static final double REACH = 64;

    @Test
    void eachCopyDrawsOnceAtItsOwnOffset() {
        List<RoomCopies.Offset> offsets = RoomCopies.offsets(ROOM, REACH);

        RoomCopyDraws.Draws draws = RoomCopyDraws.of(ROOM, offsets, FLOOR, CEILING, box -> true);

        assertEquals(offsets.size(), draws.instances(), "one instance per copy");
        for (int instance = 0; instance < offsets.size(); instance++) {
            RoomCopies.Offset translation = RoomCopyDraws.translationOf(draws.translations(), instance);
            assertEquals(offsets.get(instance).east(), translation.east(), 1.0E-4, "east of copy " + instance);
            assertEquals(offsets.get(instance).south(), translation.south(), 1.0E-4, "south of copy " + instance);
        }
    }

    @Test
    void aCopyTheCameraCannotSeeDrawsNone() {
        RoomCopies.Offset east = new RoomCopies.Offset(4.6, 0);
        RoomCopies.Offset west = new RoomCopies.Offset(-4.6, 0);
        AABB room = RoomCopyDraws.boxOf(ROOM, FLOOR, CEILING);

        RoomCopyDraws.Draws draws = RoomCopyDraws.of(ROOM, List.of(west, east), FLOOR, CEILING,
                box -> box.getCenter().x() > room.getCenter().x());

        assertEquals(List.of(east), draws.drawn(), "only the copy east of the room is seen");
    }
}
