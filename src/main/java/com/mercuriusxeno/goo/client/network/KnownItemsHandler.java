package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.network.KnownItemLearnedPayload;
import com.mercuriusxeno.goo.network.KnownItemsSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for the known-items payloads: the whole set on login
 * replaces what the connection holds, and each learning adds one item to it
 * (decision knowledge-capability-remembers-destroyed-items).
 */
public final class KnownItemsHandler {

    private KnownItemsHandler() {
    }

    /**
     * Replaces the connection's known items with the synced set.
     *
     * @param payload the full-set payload
     * @param context the network context
     */
    public static void handleSync(KnownItemsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ((KnownItemsConnection) context.listener()).receiveKnownItems(payload.known()));
    }

    /**
     * Adds the learned item to the connection's known items.
     *
     * @param payload the learning payload
     * @param context the network context
     */
    public static void handleLearned(KnownItemLearnedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            KnownItemsConnection connection = (KnownItemsConnection) context.listener();
            connection.receiveKnownItems(connection.knownItems().with(payload.item()));
        });
    }
}
