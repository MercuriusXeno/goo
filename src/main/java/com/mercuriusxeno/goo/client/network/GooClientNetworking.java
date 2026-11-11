package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ability.AbilityPoses;
import com.mercuriusxeno.goo.client.ability.ClientGateCarry;
import com.mercuriusxeno.goo.client.ability.ClientHoles;
import com.mercuriusxeno.goo.client.ability.ClientPortals;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.client.ability.GlassKnives;
import com.mercuriusxeno.goo.client.ability.GnatSeek;
import com.mercuriusxeno.goo.client.ability.HeaterFlames;
import com.mercuriusxeno.goo.client.ability.HeldSweepVisual;
import com.mercuriusxeno.goo.client.ability.LeechWisps;
import com.mercuriusxeno.goo.client.ability.MeltAbsorbs;
import com.mercuriusxeno.goo.client.ability.MeteorScorches;
import com.mercuriusxeno.goo.client.ability.PartnerView;
import com.mercuriusxeno.goo.client.ability.ReapSlashBlades;
import com.mercuriusxeno.goo.client.ability.ReapSwells;
import com.mercuriusxeno.goo.client.ability.ScanVisuals;
import com.mercuriusxeno.goo.client.ability.StreamMotes;
import com.mercuriusxeno.goo.client.ability.SunbeamVisual;
import com.mercuriusxeno.goo.client.ability.Tomes;
import com.mercuriusxeno.goo.client.ability.VitalityVisual;
import com.mercuriusxeno.goo.client.hole.ClientPortableHoles;
import com.mercuriusxeno.goo.client.outsider.ClientConjuredDoors;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.ArmPosePayload;
import com.mercuriusxeno.goo.network.BlockAfterimagePayload;
import com.mercuriusxeno.goo.network.BlockExposurePayload;
import com.mercuriusxeno.goo.network.BlockTransformPayload;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import com.mercuriusxeno.goo.network.GateCarriedPayload;
import com.mercuriusxeno.goo.network.GhostTrailPayload;
import com.mercuriusxeno.goo.network.GlassKnifePayload;
import com.mercuriusxeno.goo.network.GnatSeekPayload;
import com.mercuriusxeno.goo.network.GooFlightPayload;
import com.mercuriusxeno.goo.network.GooValueSyncPayload;
import com.mercuriusxeno.goo.network.HeaterFlamePayload;
import com.mercuriusxeno.goo.network.HeldSweepPayload;
import com.mercuriusxeno.goo.network.HolesSyncPayload;
import com.mercuriusxeno.goo.network.KnownItemLearnedPayload;
import com.mercuriusxeno.goo.network.KnownItemsSyncPayload;
import com.mercuriusxeno.goo.network.LeechPayload;
import com.mercuriusxeno.goo.network.LurkerPulsePayload;
import com.mercuriusxeno.goo.network.MeltAbsorbPayload;
import com.mercuriusxeno.goo.network.MeteorScorchPayload;
import com.mercuriusxeno.goo.network.MobHitPayload;
import com.mercuriusxeno.goo.network.ModelShrinkPayload;
import com.mercuriusxeno.goo.network.NovaRingPayload;
import com.mercuriusxeno.goo.network.OpenNamingScreenPayload;
import com.mercuriusxeno.goo.network.OpenValuesManagerPayload;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mercuriusxeno.goo.network.PartnerChunkPayload;
import com.mercuriusxeno.goo.network.PartnerChunksDropPayload;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import com.mercuriusxeno.goo.network.PortalsSyncPayload;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mercuriusxeno.goo.network.ReapSwellPayload;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import com.mercuriusxeno.goo.network.ScanWavePayload;
import com.mercuriusxeno.goo.network.SculkerHeardPayload;
import com.mercuriusxeno.goo.network.SculkerVibrationPayload;
import com.mercuriusxeno.goo.network.StreamHealedPayload;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.network.SunbeamPayload;
import com.mercuriusxeno.goo.network.TomePayload;
import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.network.TunerFeedbackPayload;
import com.mercuriusxeno.goo.network.WorkbenchReplyPayload;
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
        event.register(OpenValuesManagerPayload.TYPE, ValuesManagerHandlers::handleOpen);
        event.register(WorkbenchReplyPayload.TYPE, ValuesManagerHandlers::handleReply);
        event.register(PortalsSyncPayload.TYPE, ClientPortals::handle);
        event.register(HolesSyncPayload.TYPE, ClientHoles::handle);
        event.register(RoomEdgesSyncPayload.TYPE, ClientRoomEdges::handle);
        event.register(ConjuredDoorPayload.TYPE, ClientConjuredDoors::handle);
        event.register(GateCarriedPayload.TYPE, ClientGateCarry::handle);
        event.register(PartnerChunkPayload.TYPE, PartnerView::handle);
        event.register(PartnerChunksDropPayload.TYPE, PartnerView::handleDrop);
        event.register(PortableHolesSyncPayload.TYPE, ClientPortableHoles::handle);
        registerAbilityVisualHandlers(event);
    }

    /**
     * Registers the handlers of the payloads that draw an ability's visuals.
     *
     * @param event the client payload handler registration event
     */
    private static void registerAbilityVisualHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ChainBurnoutPayload.TYPE, ChainBurnoutHandler::handle);
        event.register(LurkerPulsePayload.TYPE, LurkerPulseHandler::handle);
        event.register(SculkerHeardPayload.TYPE, SculkerHeardHandler::handle);
        event.register(SculkerVibrationPayload.TYPE, SculkerVibrationHandler::handle);
        registerEffectHandlers(event);
    }

    /**
     * Registers the client-bound handlers of the mob and self effects' visuals.
     *
     * @param event the client payload handler registration event
     */
    private static void registerEffectHandlers(RegisterClientPayloadHandlersEvent event) {
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
        event.register(GnatSeekPayload.TYPE, GnatSeek::handle);
        event.register(StreamMotesPayload.TYPE, StreamMotes::handle);
        event.register(BlockAfterimagePayload.TYPE, AfterimageHandler::handleBlock);
    }

    /**
     * Registers the handlers for the abilities' own visuals: the held sweeps of
     * astral's Scry, crystal's Glitter and metal's Magnet,
     * glow's Sunbeam, hex's leech and tomes, frost's Nova rings and
     * leaf's Reap swells.
     *
     * @param event the client payload handler registration event
     */
    private static void registerAbilityOwnVisualHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(HeldSweepPayload.TYPE, HeldSweepVisual::onPayload);
        event.register(SunbeamPayload.TYPE, SunbeamVisual::onPayload);
        event.register(LeechPayload.TYPE, LeechWisps::handle);
        event.register(TomePayload.TYPE, Tomes::handle);
        event.register(NovaRingPayload.TYPE, NovaRingHandler::handle);
        event.register(HeaterFlamePayload.TYPE, HeaterFlames::onPayload);
        event.register(MeltAbsorbPayload.TYPE, MeltAbsorbs::onPayload);
        event.register(ReapSwellPayload.TYPE, ReapSwells::handle);
        event.register(ReapSlashPayload.TYPE, ReapSlashBlades::handle);
        event.register(OreRevealPayload.TYPE, OreRevealHandler::handle);
        event.register(MeteorScorchPayload.TYPE, MeteorScorches::onPayload);
        event.register(GlassKnifePayload.TYPE, GlassKnives::onPayload);
        event.register(ArmPosePayload.TYPE, AbilityPoses::onPayload);
        event.register(ScanWavePayload.TYPE, ScanVisuals::onPayload);
    }
}
