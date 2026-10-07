package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ability.VitalityVisual;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.network.GhostTrailPayload;
import com.mercuriusxeno.goo.network.GooFlightPayload;
import com.mercuriusxeno.goo.network.GooValueSyncPayload;
import com.mercuriusxeno.goo.network.KnownItemLearnedPayload;
import com.mercuriusxeno.goo.network.KnownItemsSyncPayload;
import com.mercuriusxeno.goo.network.MobHitPayload;
import com.mercuriusxeno.goo.network.OpenNamingScreenPayload;
import com.mercuriusxeno.goo.network.StreamHealedPayload;
import com.mercuriusxeno.goo.network.TransformationPayload;
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
        event.register(GooFlightPayload.TYPE, GooFlightHandler::handle);
        event.register(AbilitySyncPayload.TYPE, AbilitySyncHandler::handle);
        event.register(ChainBurnoutPayload.TYPE, ChainBurnoutHandler::handle);
        event.register(MobHitPayload.TYPE, MobHitHandler::handle);
        event.register(AilmentPayload.TYPE, AilmentHandler::handle);
        event.register(AfterimagePayload.TYPE, AfterimageHandler::handle);
        event.register(TransformationPayload.TYPE, TransformationHandler::handle);
        event.register(GhostTrailPayload.TYPE, GhostTrailHandler::handle);
        event.register(KnownItemsSyncPayload.TYPE, KnownItemsHandler::handleSync);
        event.register(KnownItemLearnedPayload.TYPE, KnownItemsHandler::handleLearned);
        event.register(StreamHealedPayload.TYPE, VitalityVisual::handleHealed);
    }
}
