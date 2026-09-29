package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValueConnection;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.network.GooValueSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for goo value sync packets.
 * Hands received values to the client connection on the main thread.
 */
public final class GooValueSyncHandler {

    /** Log message for received value sync. */
    private static final String LOG_RECEIVED = "Received {} goo values from server";

    private GooValueSyncHandler() {}

    /**
     * Handles the sync payload by replacing the values the connection holds.
     *
     * @param payload the sync payload data
     * @param context the network context
     */
    public static void handle(GooValueSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ((GooValueConnection) context.listener())
                    .receiveGooValues(GooValueTable.ofEffectiveValues(payload.values()));
            if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_RECEIVED, payload.values().size()); }
        });
    }
}
