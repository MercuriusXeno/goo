package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Carries the item a player picked in the known items overlay to the server, which places a ghost
 * of it in their empty main hand.
 * decision menu-places-a-ghost-item-in-the-empty-hand
 *
 * @param item the id of the item picked
 */
public record GhostItemPickPayload(Identifier item) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GhostItemPickPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ghost_item_pick"));

    /** Stream codec for encoding and decoding the payload. */
    public static final StreamCodec<ByteBuf, GhostItemPickPayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.map(GhostItemPickPayload::new, GhostItemPickPayload::item);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
