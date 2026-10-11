package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a glass shard fell from a crystal tap's spigot
 * to the point it struck, drawn falling by every client watching
 * (decision shards-drip-falls-as-a-glass-shard).
 *
 * @param from the spigot the shard fell from
 * @param to   the point it struck, the top of a mob or the landing
 */
public record ShardFallPayload(Vec3 from, Vec3 to) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ShardFallPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "shard_fall"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ShardFallPayload> STREAM_CODEC =
            StreamCodec.of(ShardFallPayload::encode, ShardFallPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, ShardFallPayload payload) {
        buf.writeDouble(payload.from.x);
        buf.writeDouble(payload.from.y);
        buf.writeDouble(payload.from.z);
        buf.writeDouble(payload.to.x);
        buf.writeDouble(payload.to.y);
        buf.writeDouble(payload.to.z);
    }

    private static ShardFallPayload decode(FriendlyByteBuf buf) {
        return new ShardFallPayload(readPoint(buf), readPoint(buf));
    }

    private static Vec3 readPoint(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
