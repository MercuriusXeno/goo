package com.mercuriusxeno.goo.ability.zone;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a player near a room's edge comes out on crossing it: one room width
 * back across each edge they stand near, and across both at a corner. The
 * server holds those places loaded, as a linked portal holds its partner's
 * chunk, so the crossing lands on ground already there however wide the room.
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 */
public final class RoomFarSide {

    /** How near an edge a player stands, in blocks, before the far side loads ahead of their crossing. */
    static final double NEAR = 32;

    private RoomFarSide() {
    }

    /**
     * A place on the floor, in world blocks.
     *
     * @param x the place's x
     * @param z the place's z
     */
    public record Spot(double x, double z) {
    }

    /**
     * The places a player would come out at across the edges they stand near.
     *
     * @param bounds the room's boundary
     * @param x      the player's x
     * @param z      the player's z
     * @return the far side of each edge within reach, and of the corner where two are
     */
    public static List<Spot> across(RoomExtent.Bounds bounds, double x, double z) {
        RoomExtent.Planes planes = RoomExtent.planesAround(
                new BlockPos(bounds.centerX(), ZoneRooms.ROOM_FLOOR_Y, bounds.centerZ()),
                bounds.extent());
        double width = planes.east() - planes.west();
        double eastward = shift(x, planes.west(), planes.east(), width);
        double southward = shift(z, planes.north(), planes.south(), width);
        List<Spot> spots = new ArrayList<>();
        if (eastward != 0) {
            spots.add(new Spot(x + eastward, z));
        }
        if (southward != 0) {
            spots.add(new Spot(x, z + southward));
        }
        if (eastward != 0 && southward != 0) {
            spots.add(new Spot(x + eastward, z + southward));
        }
        return spots;
    }

    /**
     * How far a crossing along one axis carries a player: back a width from
     * near the high plane, on a width from near the low one, nowhere between.
     *
     * @param at    the player's place along the axis
     * @param low   the room's low plane along it
     * @param high  the room's high plane along it
     * @param width the room's width
     * @return the carry along the axis, 0 far from both planes
     */
    private static double shift(double at, double low, double high, double width) {
        if (high - at <= NEAR) {
            return -width;
        }
        return at - low <= NEAR ? width : 0;
    }
}
