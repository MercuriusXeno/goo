package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Where the bodies an Outsider room holds draw in its copies: each body
 * standing in the room draws once in every copy, at its drawn place moved by
 * that copy's offset, so each copy shows the bodies the room holds, as a view
 * through a linked portal shows them. The local player draws in every copy in
 * first person too, though never at its own place, where the frame draws no
 * body of its own: the player looks across the room and sees its own back.
 * decision room-tiles-to-render-distance-held-to-the-picture
 */
public final class RoomBodyCopies {

    private static final double HALF_BLOCK = 0.5;

    private RoomBodyCopies() {
    }

    /**
     * Whether a body stands in a room: its middle inside the boundary, as the
     * room's edges carry a body across once its middle passes them.
     *
     * @param bounds the room's boundary
     * @param at     the body's drawn place
     * @return true where the room holds it
     */
    public static boolean standsIn(RoomExtent.Bounds bounds, Vec3 at) {
        return Math.abs(at.x() - bounds.centerX() - HALF_BLOCK) <= bounds.extent()
                && Math.abs(at.z() - bounds.centerZ() - HALF_BLOCK) <= bounds.extent();
    }

    /**
     * The places one body draws in the room's copies: its drawn place moved
     * by each copy's offset, and never its own place.
     *
     * @param drawn   the body's drawn place, between its last tick and this one
     * @param offsets the room's copies
     * @return one place for each copy, in the copies' order
     */
    public static List<Vec3> placesOf(Vec3 drawn, List<RoomCopies.Offset> offsets) {
        return offsets.stream().map(offset -> drawn.add(offset.east(), 0, offset.south())).toList();
    }
}
