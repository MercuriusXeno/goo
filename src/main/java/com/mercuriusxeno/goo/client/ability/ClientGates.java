package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import java.util.ArrayList;
import java.util.List;

/**
 * Tells the passage which gates the client knows linked, the local player's
 * portal pair and each open hole's mouths, so the client's own movement
 * walks into them as the server's does.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
final class ClientGates {

    private ClientGates() {
    }

    /**
     * Hands the passage the gates the client now knows linked.
     */
    static void refresh() {
        List<GatePassage.Pair> pairs = new ArrayList<>();
        ClientPortals.link().ifPresent(link -> pairs.add(new GatePassage.Pair(link.entry().gate(),
                link.exit().gate())));
        ClientHoles.holes().forEach(hole -> pairs.add(new GatePassage.Pair(hole.entry(), hole.exit())));
        GatePassage.setClientPairs(pairs);
    }
}
