package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Server-to-client payload: the block a held Tick stream hastens this tick,
 * or none where its look ends on nothing Tick can hasten, so the client
 * highlights only the block the server ticks. Sent to the streaming player
 * on each tick of the hold.
 * tick-channel-marches-squares-on-the-face
 *
 * @param ticked the block the stream hastens, empty where it hastens none
 */
public record TickAimPayload(Optional<BlockPos> ticked) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<TickAimPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "tick_aim"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, TickAimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), TickAimPayload::ticked,
            TickAimPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
