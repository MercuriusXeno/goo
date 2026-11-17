package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one of Knives' glass knives left its hover slot,
 * for every client watching to fly it along the same projectile flight the
 * server flies it on.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-and-dart-function-as-projectiles
 *
 * @param throwerId the thrower's entity id, whom the knife passes through
 * @param start     where it left its hover slot
 * @param velocity  its launch velocity, blocks per tick
 */
public record GlassKnifePayload(int throwerId, Vec3 start, Vec3 velocity) implements CustomPacketPayload {

    /** How a knife's flight ends. */
    public enum Ending {
        /** It sticks in a block, then shatters. */
        STUCK,
        /** It strikes a mob and shatters. */
        STRUCK,
        /** Its flight ran out, or it left the world, and it shatters in the air. */
        SPENT
    }

    /** Payload type ID for registration. */
    public static final Type<GlassKnifePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "glass_knife"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, GlassKnifePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GlassKnifePayload::throwerId,
            Vec3.STREAM_CODEC, GlassKnifePayload::start,
            Vec3.STREAM_CODEC, GlassKnifePayload::velocity,
            GlassKnifePayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
