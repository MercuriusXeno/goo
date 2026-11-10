package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Server-to-client payload: a scan's wave leaves the glove for its landing,
 * reaching it the ticks the server waits before the landing's program runs,
 * so the drawn wave arrives as the sound plays there.
 * click-is-a-hit-scan-drawn-as-a-delayed-wave
 *
 * @param from   the glove hand the wave leaves
 * @param to     where the wave arrives
 * @param ticks  the ticks it takes to arrive, the server's delay
 * @param visual the visual's name, which a client renderer registers under
 * @param trail  the sound the wave trails in a fading reverb on its way, empty for none
 */
public record ScanWavePayload(Vec3 from, Vec3 to, int ticks, String visual, Optional<Identifier> trail)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ScanWavePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "scan_wave"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ScanWavePayload> STREAM_CODEC =
            StreamCodec.of(ScanWavePayload::encode, ScanWavePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, ScanWavePayload payload) {
        Vec3.STREAM_CODEC.encode(buf, payload.from);
        Vec3.STREAM_CODEC.encode(buf, payload.to);
        buf.writeVarInt(payload.ticks);
        buf.writeUtf(payload.visual);
        buf.writeOptional(payload.trail, (out, id) -> Identifier.STREAM_CODEC.encode(out, id));
    }

    private static ScanWavePayload decode(FriendlyByteBuf buf) {
        return new ScanWavePayload(Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf), buf.readVarInt(),
                buf.readUtf(), buf.readOptional(Identifier.STREAM_CODEC::decode));
    }
}
