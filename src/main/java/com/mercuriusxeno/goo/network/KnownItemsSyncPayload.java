package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.KnownItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Carries a player's whole known-items set to their client on login, beside
 * the goo values (decision knowledge-capability-remembers-destroyed-items).
 *
 * @param known the player's known items
 */
public record KnownItemsSyncPayload(KnownItems known) implements CustomPacketPayload {

    /**
     * Payload type ID for registration.
     */
    public static final Type<KnownItemsSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "known_items_sync"));

    /**
     * Stream codec for encoding/decoding the payload.
     */
    public static final StreamCodec<ByteBuf, KnownItemsSyncPayload> STREAM_CODEC =
            KnownItems.STREAM_CODEC.map(KnownItemsSyncPayload::new, KnownItemsSyncPayload::known);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
