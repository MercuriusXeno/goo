package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: /goo workbench tells the calling player's client to open the values manager
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public record OpenValuesManagerPayload() implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<OpenValuesManagerPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "open_values_manager"));

    /** Stream codec for encoding/decoding: the payload carries no field. */
    public static final StreamCodec<FriendlyByteBuf, OpenValuesManagerPayload> STREAM_CODEC =
        StreamCodec.unit(new OpenValuesManagerPayload());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
