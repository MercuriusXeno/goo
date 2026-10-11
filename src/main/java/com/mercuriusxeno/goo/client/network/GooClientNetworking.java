package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ability.LeechWisps;
import com.mercuriusxeno.goo.client.ability.ReapSwells;
import com.mercuriusxeno.goo.client.ability.ScrySweep;
import com.mercuriusxeno.goo.client.ability.SunbeamVisual;
import com.mercuriusxeno.goo.client.ability.Tomes;
import com.mercuriusxeno.goo.client.ability.VitalityVisual;
import com.mercuriusxeno.goo.client.overlay.TickAim;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.BlockAfterimagePayload;
import com.mercuriusxeno.goo.network.BlockExposurePayload;
import com.mercuriusxeno.goo.network.BlockTransformPayload;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.network.DripHealedPayload;
import com.mercuriusxeno.goo.network.GhostTrailPayload;
import com.mercuriusxeno.goo.network.GooFlightPayload;
import com.mercuriusxeno.goo.network.GooValueSyncPayload;
import com.mercuriusxeno.goo.network.KnownItemLearnedPayload;
import com.mercuriusxeno.goo.network.KnownItemsSyncPayload;
import com.mercuriusxeno.goo.network.LeechPayload;
import com.mercuriusxeno.goo.network.MobHitPayload;
import com.mercuriusxeno.goo.network.ModelShrinkPayload;
import com.mercuriusxeno.goo.network.NovaRingPayload;
import com.mercuriusxeno.goo.network.OpenNamingScreenPayload;
import com.mercuriusxeno.goo.network.ReapSwellPayload;
import com.mercuriusxeno.goo.network.ScryPayload;
import com.mercuriusxeno.goo.network.StreamHealedPayload;
import com.mercuriusxeno.goo.network.SunbeamPayload;
import com.mercuriusxeno.goo.network.TickAimPayload;
import com.mercuriusxeno.goo.network.TomePayload;
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
        event.register(KnownItemsSyncPayload.TYPE, KnownItemsHandler::handleSync);
        event.register(KnownItemLearnedPayload.TYPE, KnownItemsHandler::handleLearned);
        registerAbilityVisualHandlers(event);
    }

    /**
     * Registers the handlers of the payloads that draw an ability's visuals.
     *
     * @param event the client payload handler registration event
     */
    private static void registerAbilityVisualHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ChainBurnoutPayload.TYPE, ChainBurnoutHandler::handle);
        event.register(MobHitPayload.TYPE, MobHitHandler::handle);
        event.register(AilmentPayload.TYPE, AilmentHandler::handle);
        registerAbilityOwnVisualHandlers(event);
        event.register(BlockTransformPayload.TYPE, BlockTransformHandler::handle);
        event.register(BlockExposurePayload.TYPE, BlockTransformHandler::handleExposure);
        event.register(AfterimagePayload.TYPE, AfterimageHandler::handle);
        event.register(TransformationPayload.TYPE, TransformationHandler::handle);
        event.register(ModelShrinkPayload.TYPE, TransformationHandler::handleShrink);
        event.register(GhostTrailPayload.TYPE, GhostTrailHandler::handle);
        event.register(StreamHealedPayload.TYPE, VitalityVisual::handleHealed);
        event.register(DripHealedPayload.TYPE, VitalityVisual::handleDripHealed);
        event.register(BlockAfterimagePayload.TYPE, AfterimageHandler::handleBlock);
    }

    /**
     * Registers the handlers for the abilities' own visuals: glow's Scry
     * and Sunbeam, hex's leech and tomes, frost's Nova rings and
     * leaf's Reap swells.
     *
     * @param event the client payload handler registration event
     */
    private static void registerAbilityOwnVisualHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ScryPayload.TYPE, ScrySweep::onPayload);
        event.register(SunbeamPayload.TYPE, SunbeamVisual::onPayload);
        event.register(LeechPayload.TYPE, LeechWisps::handle);
        event.register(TomePayload.TYPE, Tomes::handle);
        event.register(NovaRingPayload.TYPE, NovaRingHandler::handle);
        event.register(ReapSwellPayload.TYPE, ReapSwells::handle);
        event.register(TickAimPayload.TYPE, TickAim::handle);
    }
}
