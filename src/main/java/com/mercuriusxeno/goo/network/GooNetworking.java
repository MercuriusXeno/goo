package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all network payloads for the Goo mod.
 * Uses mod event bus via {@code @EventBusSubscriber} to handle payload registration.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooNetworking {

    /** Protocol version for network payload registration. */
    private static final String PROTOCOL_VERSION = "1";

    private GooNetworking() {}

    /**
     * Registers all network payloads for both directions.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(Goo.MODID).versioned(PROTOCOL_VERSION);
        registerClientPayloads(r);
        registerServerPayloads(r);
    }

    /** Registers client-bound payloads by type and codec alone; their handlers register from
     * client.network.GooClientNetworking on Dist.CLIENT only
     * (decision diagnose-then-fix-server-link-and-value-race).
     *
     * @param r the payload registrar
     */
    private static void registerClientPayloads(PayloadRegistrar r) {
        r.playToClient(GooValueSyncPayload.TYPE, GooValueSyncPayload.STREAM_CODEC);
        r.playToClient(OpenNamingScreenPayload.TYPE, OpenNamingScreenPayload.STREAM_CODEC);
        r.playToClient(TunerFeedbackPayload.TYPE, TunerFeedbackPayload.STREAM_CODEC);
        r.playToClient(GooFlightPayload.TYPE, GooFlightPayload.STREAM_CODEC);
        r.playToClient(AbilitySyncPayload.TYPE, AbilitySyncPayload.STREAM_CODEC);
        r.playToClient(ChainBurnoutPayload.TYPE, ChainBurnoutPayload.STREAM_CODEC);
        r.playToClient(MobHitPayload.TYPE, MobHitPayload.STREAM_CODEC);
        r.playToClient(AilmentPayload.TYPE, AilmentPayload.STREAM_CODEC);
        r.playToClient(LeechPayload.TYPE, LeechPayload.STREAM_CODEC);
        r.playToClient(TomePayload.TYPE, TomePayload.STREAM_CODEC);
        r.playToClient(NovaRingPayload.TYPE, NovaRingPayload.STREAM_CODEC);
        r.playToClient(BlockTransformPayload.TYPE, BlockTransformPayload.STREAM_CODEC);
        r.playToClient(BlockExposurePayload.TYPE, BlockExposurePayload.STREAM_CODEC);
        r.playToClient(AfterimagePayload.TYPE, AfterimagePayload.STREAM_CODEC);
        r.playToClient(TransformationPayload.TYPE, TransformationPayload.STREAM_CODEC);
        r.playToClient(GhostTrailPayload.TYPE, GhostTrailPayload.STREAM_CODEC);
        r.playToClient(KnownItemsSyncPayload.TYPE, KnownItemsSyncPayload.STREAM_CODEC);
        r.playToClient(KnownItemLearnedPayload.TYPE, KnownItemLearnedPayload.STREAM_CODEC);
        r.playToClient(StreamHealedPayload.TYPE, StreamHealedPayload.STREAM_CODEC);
        r.playToClient(DripHealedPayload.TYPE, DripHealedPayload.STREAM_CODEC);
    }

    /** Registers server-bound payloads.
     *
     * @param r the payload registrar
     */
    private static void registerServerPayloads(PayloadRegistrar r) {
        r.playToServer(CanisterRenamePayload.TYPE, CanisterRenamePayload.STREAM_CODEC, CanisterRenameHandler::handle);
        r.playToServer(GooStreamPayload.TYPE, GooStreamPayload.STREAM_CODEC, GooStreamHandler::handle);
        r.playToServer(CanisterUnlinkPayload.TYPE, CanisterUnlinkPayload.STREAM_CODEC, CanisterUnlinkHandler::handle);
        r.playToServer(GooThrowPayload.TYPE, GooThrowPayload.STREAM_CODEC, GooThrowHandler::handle);
        r.playToServer(GooDragCastPayload.TYPE, GooDragCastPayload.STREAM_CODEC, GooDragCastHandler::handle);
        r.playToServer(GooChargePayload.TYPE, GooChargePayload.STREAM_CODEC, GooThrowHandler::handleCharge);
        r.playToServer(GloveSelectPayload.TYPE, GloveSelectPayload.STREAM_CODEC, GloveSelectHandler::handle);
    }
}
