package com.mercuriusxeno.goo.ability.zone;

/**
 * Where Zone's rooms stand in goo:pocket: room centers laid out on the
 * same square spiral the platform grows along, a million blocks apart, as the
 * operator ruled, so no platform ever meets another room. The world border
 * holds the rooms of the spiral's first 29 rings; a player past that shares
 * the last room.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public final class RoomGrid {

    /** Blocks between one room's center and the next. */
    public static final int SPACING = 1_000_000;
    /** The rooms the world border holds: the spiral's rings out to 29, 59 rooms across. */
    public static final int CAPACITY = 3481;

    private RoomGrid() {
    }

    /**
     * A room's center on the floor plane.
     *
     * @param room the room number
     * @return the center's x and z
     */
    public static PlatformSpiral.Cell centerOf(int room) {
        PlatformSpiral.Cell spot = PlatformSpiral.cellAt(Math.min(room, CAPACITY - 1));
        return new PlatformSpiral.Cell(spot.x() * SPACING, spot.z() * SPACING);
    }
}
