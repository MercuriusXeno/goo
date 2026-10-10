package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.TomeKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a player's tome plays its choreography, sent to
 * the players tracking them and to the player, so every client draws the
 * floating book or books in front of them.
 * enchant-book-with-a-purple-afterimage
 * fuse-two-books-for-hex-goo
 *
 * @param playerId the id of the player the tome floats before
 * @param kind     the choreography played
 */
public record TomePayload(int playerId, TomeKind kind) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<TomePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "tome"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, TomePayload> STREAM_CODEC =
            StreamCodec.of(TomePayload::encode, TomePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, TomePayload payload) {
        buf.writeVarInt(payload.playerId);
        buf.writeEnum(payload.kind);
    }

    private static TomePayload decode(FriendlyByteBuf buf) {
        return new TomePayload(buf.readVarInt(), buf.readEnum(TomeKind.class));
    }
}
