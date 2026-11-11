package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;
import java.util.Optional;

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
}
