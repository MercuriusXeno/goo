package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a watching marker saw an entity this near, so
 * the client draws its orb brighter and pulsing faster the nearer it stands.
 * decision lurker-blob-brightens-then-detonates
 *
 * @param pos      the marker's block position
 * @param distance the nearest watched entity's distance in blocks
 * @param radius   the radius the marker watches, in blocks
 */
public record LurkerPulsePayload(BlockPos pos, float distance, float radius) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<LurkerPulsePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "lurker_pulse"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, LurkerPulsePayload> STREAM_CODEC =
        StreamCodec.of(LurkerPulsePayload::encode, LurkerPulsePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * How near the watched entity stands, from 0 at the watch's edge to 1 on the marker.
     *
     * @return the closeness, clamped to [0, 1]
     */
    public float closeness() {
        if (radius <= 0f) {
            return 1f;
        }
        return Math.clamp(1f - distance / radius, 0f, 1f);
    }

    /**
     * Sends the pulse to every player tracking its chunk; a listener that
     * never negotiated the mod's channels gets none.
     *
     * @param level the server level the marker stands in
     */
    public void sendToTracking(ServerLevel level) {
        ChunkViewerSends.send(level, pos, this, null);
    }

    /**
     * Writes the payload to the buffer.
     *
     * @param buf     the output buffer
     * @param payload the payload to encode
     */
    private static void encode(FriendlyByteBuf buf, LurkerPulsePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeFloat(payload.distance);
        buf.writeFloat(payload.radius);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static LurkerPulsePayload decode(FriendlyByteBuf buf) {
        return new LurkerPulsePayload(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
    }
}
