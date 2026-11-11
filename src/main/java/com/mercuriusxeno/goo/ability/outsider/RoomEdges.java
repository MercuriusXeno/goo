package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * An Outsider room's four edges as two linked gate pairs, so the room wraps:
 * the square its {@link RoomExtent} bounds has a gate on each side, standing
 * on the boundary's plane, even partway through a block, facing into the
 * room, east linked to west and south to north. Each pair faces opposite
 * ways with the same up, so the passage through it moves a body twice the
 * extent and turns nothing, and a body walking off one side carries on in
 * from the other. Each edge opens over its whole rectangle, from the void
 * catch's depth up to the pocket's build height.
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 * decision grown-extent-is-the-one-wrap-and-build-boundary
 */
public final class RoomEdges {

    /** The top of every room edge: the pocket's build height. */
    static final int ROOM_CEILING_Y = 256;
    private static final Vec3 UP = Direction.UP.getUnitVec3();

    private RoomEdges() {
    }

    /**
     * A room's four edges.
     *
     * @param east  the east edge, facing west
     * @param west  the west edge, facing east
     * @param south the south edge, facing north
     * @param north the north edge, facing south
     */
    public record Edges(Gate east, Gate west, Gate south, Gate north) {

        /**
         * @return east paired with west and south with north
         */
        public List<GatePassage.Pair> pairs() {
            return List.of(new GatePassage.Pair(east, west), new GatePassage.Pair(south, north));
        }
    }

    /**
     * The edges around a room: each plane exactly the room's extent from the
     * middle of its center voidstone, partway through a block where the
     * extent ends there, so the room repeats every twice its extent.
     * decision grown-extent-is-the-one-wrap-and-build-boundary
     *
     * @param center the room's center voidstone
     * @param extent the room's extent
     * @return the four edges
     */
    public static Edges around(BlockPos center, double extent) {
        RoomExtent.Planes planes = RoomExtent.planesAround(center, extent);
        double west = planes.west();
        double east = planes.east();
        double north = planes.north();
        double south = planes.south();
        int bottom = OutsiderRooms.ROOM_FLOOR_Y - OutsiderRooms.CATCH_DEPTH;
        return new Edges(
                edge(new AABB(east, bottom, north, east + 1, ROOM_CEILING_Y, south), Direction.WEST),
                edge(new AABB(west - 1, bottom, north, west, ROOM_CEILING_Y, south), Direction.EAST),
                edge(new AABB(west, bottom, south, east, ROOM_CEILING_Y, south + 1), Direction.NORTH),
                edge(new AABB(west, bottom, north - 1, east, ROOM_CEILING_Y, north), Direction.SOUTH));
    }

    /**
     * A room's edge pairs.
     *
     * @param room   the room number
     * @param extent the room's extent
     * @return east paired with west and south with north
     */
    public static List<GatePassage.Pair> pairsOf(int room, double extent) {
        return around(OutsiderRooms.voidstoneOf(room), extent).pairs();
    }

    /**
     * Every handed-out room's edge pairs.
     *
     * @param server the server
     * @return each room's pairs
     */
    public static List<GatePassage.Pair> pairsOf(MinecraftServer server) {
        List<GatePassage.Pair> pairs = new ArrayList<>();
        VoidPockets.get(server.overworld()).extentsByRoom()
                .forEach((room, extent) -> pairs.addAll(pairsOf(room, extent)));
        return pairs;
    }

    private static Gate edge(AABB column, Direction inward) {
        return new Gate(OutsiderRooms.POCKET, column, inward, UP, Gate.Mask.RECTANGLE);
    }
}
