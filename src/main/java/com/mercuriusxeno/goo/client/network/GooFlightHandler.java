package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.throwing.GooFlightManager;
import com.mercuriusxeno.goo.network.GooFlightPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for goo flight broadcasts. Receives the flight data
 * and registers it with GooFlightManager for rendering the projectile arc.
 */
public final class GooFlightHandler {

    /** Log message for received flight payloads. */
    private static final String LOG_FLIGHT_RECEIVED = "Flight received: {} -> target in {} ticks";

    private GooFlightHandler() {}

    /**
     * Handles the flight payload on the client render thread.
     *
     * @param payload the flight payload data
     * @param context the network context
     */
    public static void handle(GooFlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_FLIGHT_RECEIVED, payload.gooTypeId(), payload.travelTicks()); }
            GooFlightManager.addFlight(payload);
        });
    }
}
