package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.BlobFlightPayload;
import com.mercuriusxeno.goo.network.GooValueSyncPayload;
import com.mercuriusxeno.goo.network.OpenNamingScreenPayload;
import com.mercuriusxeno.goo.network.TunerFeedbackPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Registers the handlers of the client-bound payloads that
 * {@link com.mercuriusxeno.goo.network.GooNetworking} declares. A dedicated
 * server never loads this class, so it never links a handler that reaches a
 * Screen or Minecraft (decision diagnose-then-fix-server-link-and-value-race).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooClientNetworking {

    private GooClientNetworking() {}

    /**
     * Registers every client-bound payload handler.
     *
     * @param event the client payload handler registration event
     */
    @SubscribeEvent
    public static void registerHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(GooValueSyncPayload.TYPE, GooValueSyncHandler::handle);
        event.register(OpenNamingScreenPayload.TYPE, OpenNamingScreenHandler::handle);
        event.register(TunerFeedbackPayload.TYPE, TunerFeedbackHandler::handle);
        event.register(BlobFlightPayload.TYPE, BlobFlightHandler::handle);
        event.register(AbilitySyncPayload.TYPE, AbilitySyncHandler::handle);
    }
}
