package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a sound played past vanilla's range that a
 * Sculker hears from far off, so its client sends
 * the sound's rings out and outlines the mob it came from.
 * decision sculker-sight-persists-where-echoes-have-touched
 *
 * @param x     where the sound played, east
 * @param y     where the sound played, up
 * @param z     where the sound played, south
 * @param sound the sound's id, which its rank is read from
 */
public record SculkerHeardPayload(double x, double y, double z, String sound)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SculkerHeardPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "sculker_heard"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SculkerHeardPayload> STREAM_CODEC =
        StreamCodec.of(SculkerHeardPayload::encode, SculkerHeardPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, SculkerHeardPayload payload) {
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeUtf(payload.sound);
    }

    private static SculkerHeardPayload decode(FriendlyByteBuf buf) {
        return new SculkerHeardPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf());
    }
}
