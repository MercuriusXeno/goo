package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The edges of the Outsider room the local player stands in, as the server
 * last told them, each pair carrying the player to the opposite side, and
 * the boundary they stand on, which the voidstone's stars read.
 * outsider-room-wraps-and-repeats-at-the-platform-edge
 * decision boundary-cut-voidstone-draws-without-its-stars
 */
public final class ClientRoomEdges {

    private static List<GatePassage.Pair> edges = List.of();
    private static Optional<RoomExtent.Bounds> bounds = Optional.empty();

    private ClientRoomEdges() {
    }

    /**
     * Takes the room edges the server sent.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(RoomEdgesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> take(payload));
    }

    /**
     * Holds the room edges a payload names and the boundary they stand on,
     * and hands the edges to the passage.
     *
     * @param payload the sync payload
     */
    public static void take(RoomEdgesSyncPayload payload) {
        edges = List.copyOf(payload.edges());
        bounds = RoomEdges.boundsOf(edges);
        ClientGates.refresh();
    }

    /**
     * @return the room's edge pairs, east with west and south with north
     */
    public static List<GatePassage.Pair> edges() {
        return edges;
    }

    /**
     * @return the boundary of the room the player stands in, empty outside one
     */
    public static Optional<RoomExtent.Bounds> bounds() {
        return bounds;
    }

    /**
     * The extent of the Outsider room the local player stands in, as the
     * server last sent it: present only in the pocket with a room's edges held.
     * decision outsider-panel-reads-the-seconds-the-void-affords
     *
     * @param dimension the level the local player stands in
     * @return the room's extent, empty outside the pocket
     */
    public static OptionalDouble extentWhereStanding(ResourceKey<Level> dimension) {
        return dimension == OutsiderRooms.POCKET
                ? bounds.map(room -> OptionalDouble.of(room.extent())).orElse(OptionalDouble.empty())
                : OptionalDouble.empty();
    }
}
