package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.HolesSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;

/**
 * The Quantum holes standing open in the local player's level as the server
 * last told them, each as its two mouths.
 * hole-closes-the-space-to-an-opening-ahead
 */
public final class ClientHoles {

    private static List<HolesSyncPayload.Pair> holes = List.of();

    private ClientHoles() {
    }

    /**
     * Takes the holes the server sent.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(HolesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            holes = List.copyOf(payload.holes());
            ClientGates.refresh();
        });
    }

    /**
     * @return each open hole's two mouths
     */
    public static List<HolesSyncPayload.Pair> holes() {
        return holes;
    }
}
