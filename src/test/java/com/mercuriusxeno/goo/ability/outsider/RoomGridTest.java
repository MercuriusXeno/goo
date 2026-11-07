package com.mercuriusxeno.goo.ability.outsider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers where Outsider's rooms stand: a million blocks apart and inside the world border. */
class RoomGridTest {

    /** The world border's half width, which every room's center stands inside. */
    private static final long BORDER = 29_999_984L;
    private static final int CHECKED = 120;

    @Test
    void everyRoomStandsAMillionBlocksFromEveryOther() {
        for (int first = 0; first < CHECKED; first++) {
            for (int second = first + 1; second < CHECKED; second++) {
                PlatformSpiral.Cell a = RoomGrid.centerOf(first);
                PlatformSpiral.Cell b = RoomGrid.centerOf(second);
                long apart = Math.max(Math.abs((long) a.x() - b.x()), Math.abs((long) a.z() - b.z()));

                assertTrue(apart >= RoomGrid.SPACING,
                        "rooms " + first + " at " + a + " and " + second + " at " + b + " stand " + apart + " apart");
            }
        }
    }

    @Test
    void theLastRoomStandsInsideTheWorldBorder() {
        PlatformSpiral.Cell last = RoomGrid.centerOf(RoomGrid.CAPACITY - 1);

        assertTrue(Math.abs((long) last.x()) < BORDER && Math.abs((long) last.z()) < BORDER, "last room at " + last);
    }

    @Test
    void roomsPastTheCapacityShareTheLast() {
        assertEquals(RoomGrid.centerOf(RoomGrid.CAPACITY - 1), RoomGrid.centerOf(RoomGrid.CAPACITY + 5));
    }
}
