package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a leech heal, life drawn from a victim to the
 * entity it heals, sent to the players tracking the victim so every client
 * draws dark purple wisps flowing from one to the other. Lifetap's hits and
 * Drain's field both send it.
 * lifetap-trades-regen-for-leech
 *
 * @param victimId the id of the entity the life is drawn from
 * @param healedId the id of the entity it heals
 */
public record LeechPayload(int victimId, int healedId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<LeechPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "leech"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, LeechPayload> STREAM_CODEC =
            StreamCodec.of(LeechPayload::encode, LeechPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, LeechPayload payload) {
        buf.writeVarInt(payload.victimId);
        buf.writeVarInt(payload.healedId);
    }

    private static LeechPayload decode(FriendlyByteBuf buf) {
        return new LeechPayload(buf.readVarInt(), buf.readVarInt());
    }
}
