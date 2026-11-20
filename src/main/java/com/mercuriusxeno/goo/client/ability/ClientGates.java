package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import java.util.ArrayList;
import java.util.List;

/**
 * Tells the passage which gates the client knows linked, the local player's
 * portal pair, each open tunnel's mouths and the edges of the Zone room it
 * stands in, so the client's own movement walks into them as the server's does.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 * outsider-room-wraps-and-repeats-at-the-platform-edge
 */
final class ClientGates {

    private ClientGates() {
    }

    /**
     * Hands the passage the gates the client now knows linked.
     */
    static void refresh() {
        List<GatePassage.Pair> pairs = new ArrayList<>();
        ClientPortals.links().forEach(link -> pairs.add(new GatePassage.Pair(link.entry().gate(),
                link.exit().gate())));
        ClientTunnels.tunnels().forEach(tunnel -> pairs.add(new GatePassage.Pair(tunnel.entry(), tunnel.exit())));
        pairs.addAll(ClientRoomEdges.edges());
        GatePassage.setClientPairs(pairs);
    }
}
