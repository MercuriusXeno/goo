package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;

/**
 * The edges of the Outsider room the local player stands in, as the server
 * last told them, each pair carrying the player to the opposite side.
 * outsider-room-wraps-and-repeats-at-the-platform-edge
 */
public final class ClientRoomEdges {

    private static List<GatePassage.Pair> edges = List.of();

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
     * Holds the room edges a payload names and hands them to the passage.
     *
     * @param payload the sync payload
     */
    static void take(RoomEdgesSyncPayload payload) {
        edges = List.copyOf(payload.edges());
        ClientGates.refresh();
    }

    /**
     * @return the room's edge pairs, east with west and south with north
     */
    public static List<GatePassage.Pair> edges() {
        return edges;
    }
}
