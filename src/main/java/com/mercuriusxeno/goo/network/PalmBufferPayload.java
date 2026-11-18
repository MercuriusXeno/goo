package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a caster's palm blob buffer for the ability
 * selected in hand, sent to the caster and the players watching whenever the
 * fill strays from what the last sync's speed and cap would grow it to, so
 * every client draws the blob at the server's fill.
 * decision palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume
 *
 * @param casterId the caster's entity id
 * @param fill     the fill, in half percents, or {@link #NONE} for a selection with no buffer
 * @param speed    the refill a tick, in half percents
 * @param cap      the most the fill refills to, in half percents
 */
public record PalmBufferPayload(int casterId, int fill, int speed, int cap) implements CustomPacketPayload {

    /** The fill sent for a caster whose selection keeps no buffer. */
    public static final int NONE = -1;

    /** Payload type ID for registration. */
    public static final Type<PalmBufferPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "palm_buffer"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PalmBufferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PalmBufferPayload::casterId,
            ByteBufCodecs.INT, PalmBufferPayload::fill,
            ByteBufCodecs.VAR_INT, PalmBufferPayload::speed,
            ByteBufCodecs.VAR_INT, PalmBufferPayload::cap,
            PalmBufferPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}