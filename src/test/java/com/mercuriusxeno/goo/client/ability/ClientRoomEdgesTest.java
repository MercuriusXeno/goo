package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.PlatformSpiral;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the client taking its room's edges: the passage's client pairs hold
 * the four edges, paired, once the payload is handled
 * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class ClientRoomEdgesTest {

    @AfterEach
    void forgetTheEdges() {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(List.of()));
        GatePassage.setClientPairs(List.of());
    }

    @Test
    void handlingTheSyncHandsThePassageTheRoomsEdgePairs() {
        List<GatePassage.Pair> edges = RoomEdges.around(OutsiderRooms.voidstoneOf(3), PlatformSpiral.START_CELLS)
                .pairs();

        ClientRoomEdges.take(new RoomEdgesSyncPayload(edges));

        assertEquals(edges, GatePassage.clientPairs());
    }
}
