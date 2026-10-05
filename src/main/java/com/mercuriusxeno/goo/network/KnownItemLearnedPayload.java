package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Carries one item a player just learned to their client, as the crucible
 * melts it (decision knowledge-capability-remembers-destroyed-items).
 *
 * @param item the id of the item learned
 */
public record KnownItemLearnedPayload(Identifier item) implements CustomPacketPayload {

    /**
     * Payload type ID for registration.
     */
    public static final Type<KnownItemLearnedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "known_item_learned"));

    /**
     * Stream codec for encoding/decoding the payload.
     */
    public static final StreamCodec<ByteBuf, KnownItemLearnedPayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.map(KnownItemLearnedPayload::new, KnownItemLearnedPayload::item);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
