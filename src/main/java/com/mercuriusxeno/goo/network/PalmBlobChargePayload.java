package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: one tick the player's glove holds a charge, a
 * press previewing before its release, which sends nothing else until it
 * lets go; the server passes it on to the players watching, so they see the
 * player's palm blob lifted through the charge.
 * every-ability-casts-from-the-lifted-palm-blob
 */
public record PalmBlobChargePayload() implements CustomPacketPayload {

    /** The one payload, since it carries nothing. */
    public static final PalmBlobChargePayload INSTANCE = new PalmBlobChargePayload();

    /** Payload type ID for registration. */
    public static final Type<PalmBlobChargePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "palm_blob_charge"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PalmBlobChargePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Passes the charge tick on to the players watching a sender holding a glove.
     *
     * @param payload the charge tick
     * @param context the network context
     */
    public static void handle(PalmBlobChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && GooThrowHandler.validateGlove(player)) {
                sendLift(player);
            }
        });
    }

    /**
     * Tells the players watching a caster that the caster's palm blob is lifted this tick.
     *
     * @param caster the caster holding a stream, a channel or a charge
     */
    public static void sendLift(ServerPlayer caster) {
        EntityVisuals.sendToTrackers(caster, new PalmBlobLiftPayload(caster.getId(), caster.getEyePosition(), false));
    }

    /**
     * Tells the players watching a caster that a durative use landed, spending the caster's lifted blob.
     * durative-self-abilities-wash-over-the-view-in-place-of-eating
     *
     * @param caster the caster whose durative use landed
     */
    public static void sendSpent(ServerPlayer caster) {
        EntityVisuals.sendToTrackers(caster, new PalmBlobLiftPayload(caster.getId(), caster.getEyePosition(), true));
    }
}
