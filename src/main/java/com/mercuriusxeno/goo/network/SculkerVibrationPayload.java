package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a vibration that went off within a Sculker's
 * render distance, ranked by vanilla, which the client joins to the sound
 * its noise played so that sound rings once at the vibration's frequency.
 * decision sculker-echo-intensity-follows-vibration-rank
 *
 * @param x    where it went off, east
 * @param y    where it went off, up
 * @param z    where it went off, south
 * @param rank its vibration frequency, 1 to 15
 */
public record SculkerVibrationPayload(double x, double y, double z, int rank) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SculkerVibrationPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "sculker_vibration"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SculkerVibrationPayload> STREAM_CODEC =
        StreamCodec.of(SculkerVibrationPayload::encode, SculkerVibrationPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, SculkerVibrationPayload payload) {
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeVarInt(payload.rank);
    }

    private static SculkerVibrationPayload decode(FriendlyByteBuf buf) {
        return new SculkerVibrationPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }
}
