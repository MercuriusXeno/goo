package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the bodies an Outsider room holds draw across the tiled room: each
 * body standing in the room draws once in every copy, at its drawn place
 * moved by that copy's offset, so each copy shows the bodies the room holds,
 * as a view through a linked portal shows them. The frame draws a body at
 * its own place, save the camera's own body in first person, which draws
 * there not at all; that body still draws in every copy, so the player looks
 * across the room and sees its own back.
 * decision room-tiles-to-render-distance-held-to-the-picture
 */
public final class RoomBodyCopies {

    private static final double HALF_BLOCK = 0.5;

    private RoomBodyCopies() {
    }

    /**
     * One place a body draws, and whether the frame draws it there or the room's copies do.
     *
     * @param at      the place
     * @param byFrame true where the frame's own body pass draws it, false where the copies do
     */
    public record Draw(Vec3 at, boolean byFrame) {
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
     * Every place one body draws across the tiled room: its own place, where
     * the frame draws it unless it is the camera's body in first person, and
     * its drawn place moved by each copy's offset, where the copies draw it.
     *
     * @param drawn      the body's drawn place, between its last tick and this one
     * @param offsets    the room's copies
     * @param cameraBody whether the body is the one the camera looks out of
     * @param detached   whether the camera stands off that body, in third person
     * @return the frame's draw first where it draws one, then one for each copy in the copies' order
     */
    public static List<Draw> drawsOf(Vec3 drawn, List<RoomCopies.Offset> offsets, boolean cameraBody,
            boolean detached) {
        List<Draw> draws = new ArrayList<>();
        if (!cameraBody || detached) {
            draws.add(new Draw(drawn, true));
        }
        offsets.forEach(offset -> draws.add(new Draw(drawn.add(offset.east(), 0, offset.south()), false)));
        return draws;
    }
}
