package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one of Shards' glass knives left the hand, for
 * every client watching to fly along the same arc to where it ends
 * (decision shards-sling-then-morph-to-flechettes).
 *
 * @param start    where it left the hand
 * @param velocity its velocity leaving the hand, in blocks a tick
 * @param ticks    the ticks it flies before it ends
 * @param end      where it ends: in a block, in a mob, or in the air once spent
 * @param ending   how it ends, a {@link Ending} ordinal
 */
public record GlassKnifePayload(Vec3 start, Vec3 velocity, int ticks, Vec3 end, int ending)
        implements CustomPacketPayload {

    /** How a knife's flight ends. */
    public enum Ending {
        /** It sticks in a block, then shatters. */
        STUCK,
        /** It strikes a mob and shatters. */
        STRUCK,
        /** It flew its whole flight and shatters in the air. */
        SPENT
    }

    /** Payload type ID for registration. */
    public static final Type<GlassKnifePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "glass_knife"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GlassKnifePayload> STREAM_CODEC =
            StreamCodec.of(GlassKnifePayload::encode, GlassKnifePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GlassKnifePayload payload) {
        buf.writeDouble(payload.start.x);
        buf.writeDouble(payload.start.y);
        buf.writeDouble(payload.start.z);
        buf.writeDouble(payload.velocity.x);
        buf.writeDouble(payload.velocity.y);
        buf.writeDouble(payload.velocity.z);
        buf.writeVarInt(payload.ticks);
        buf.writeDouble(payload.end.x);
        buf.writeDouble(payload.end.y);
        buf.writeDouble(payload.end.z);
        buf.writeVarInt(payload.ending);
    }

    private static GlassKnifePayload decode(FriendlyByteBuf buf) {
        Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 velocity = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int ticks = buf.readVarInt();
        Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new GlassKnifePayload(start, velocity, ticks, end, buf.readVarInt());
    }
}
