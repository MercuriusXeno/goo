package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the spirits in Leech goo bit a living thing,
 * sent to the players tracking it so every client bursts hex wisps over its
 * body, the soul motes Drain bursts at each strike.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 *
 * @param victimId the id of the entity bitten
 */
public record SpiritBitePayload(int victimId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SpiritBitePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "spirit_bite"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SpiritBitePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.victimId),
                    buf -> new SpiritBitePayload(buf.readVarInt()));

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
