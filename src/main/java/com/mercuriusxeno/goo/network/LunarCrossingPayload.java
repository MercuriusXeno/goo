package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.LunarCrossingShift;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the server is carrying the local player across lunar's seam, sent just before the teleport,
 * so the client moves the chunks it holds over by the crossing's shift rather than waiting for them again.
 * decision moon-wraps-at-its-border-like-the-zone
 *
 * @param chunksX the crossing's shift on x, in chunks
 * @param chunksZ the crossing's shift on z, in chunks
 */
public record LunarCrossingPayload(int chunksX, int chunksZ) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<LunarCrossingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_crossing"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, LunarCrossingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LunarCrossingPayload::chunksX,
            ByteBufCodecs.VAR_INT, LunarCrossingPayload::chunksZ,
            LunarCrossingPayload::new);

    /**
     * The payload telling a client of a crossing's shift.
     *
     * @param shift the crossing's shift
     * @return the payload
     */
    public static LunarCrossingPayload of(LunarCrossingShift shift) {
        return new LunarCrossingPayload(shift.chunksX(), shift.chunksZ());
    }

    /**
     * The crossing's shift this payload tells of.
     *
     * @return the shift
     */
    public LunarCrossingShift shift() {
        return new LunarCrossingShift(chunksX, chunksZ);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
