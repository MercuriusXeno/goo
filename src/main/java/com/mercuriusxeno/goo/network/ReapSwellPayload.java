package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a Reap swell, a whole sphere of Growth's breeze
 * swelling from where the blob struck out to Reap's radius and thinning away.
 * Sent to the players watching the struck cell's chunk.
 * reap-breeze-harvests-and-replants
 *
 * @param center     where the swell starts, the point the blob struck
 * @param radius     the radius the swell reaches, in blocks
 * @param swellTicks the game ticks the swell takes to reach its radius
 */
public record ReapSwellPayload(Vec3 center, float radius, int swellTicks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ReapSwellPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "reap_swell"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ReapSwellPayload> STREAM_CODEC =
        StreamCodec.of(ReapSwellPayload::encode, ReapSwellPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, ReapSwellPayload payload) {
        Vec3.STREAM_CODEC.encode(buf, payload.center);
        buf.writeFloat(payload.radius);
        buf.writeVarInt(payload.swellTicks);
    }

    private static ReapSwellPayload decode(FriendlyByteBuf buf) {
        return new ReapSwellPayload(Vec3.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readVarInt());
    }
}
