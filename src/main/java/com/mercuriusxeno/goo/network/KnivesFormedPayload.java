package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the knives a caster's Knives hold has formed,
 * sent to the caster and the players watching each time the count changes,
 * 0 once the hold ends, so every client draws the server's fan.
 * decision knives-emerge-from-the-blob-and-hover-in-a-fan
 * decision knives-form-in-fibonacci-stages-to-twelve
 *
 * @param casterId the caster's entity id
 * @param formed   the knives formed, 0 for no hold
 */
public record KnivesFormedPayload(int casterId, int formed) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<KnivesFormedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "knives_formed"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, KnivesFormedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KnivesFormedPayload::casterId,
            ByteBufCodecs.VAR_INT, KnivesFormedPayload::formed,
            KnivesFormedPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
