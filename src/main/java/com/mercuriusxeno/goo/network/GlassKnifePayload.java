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
 * Server-to-client payload: one of Knives' glass knives left its fan slot
 * homing on its mob, for every client watching to fly it the same way, each
 * tick turning toward where the mob stands on that client.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-home-on-their-painted-mob
 *
 * @param start     where it left its fan slot
 * @param mobId     the entity id of the mob it homes on
 * @param lastKnown where the mob stood as the knife left, which the knife flies to should the mob be gone
 */
public record GlassKnifePayload(Vec3 start, int mobId, Vec3 lastKnown) implements CustomPacketPayload {

    /** How a knife's flight ends. */
    public enum Ending {
        /** It sticks in a block, then shatters. */
        STUCK,
        /** It strikes a mob and shatters. */
        STRUCK,
        /** Its mob was gone, or its flight ran out, and it shatters in the air. */
        SPENT
    }

    /** Payload type ID for registration. */
    public static final Type<GlassKnifePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "glass_knife"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, GlassKnifePayload> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, GlassKnifePayload::start,
            ByteBufCodecs.VAR_INT, GlassKnifePayload::mobId,
            Vec3.STREAM_CODEC, GlassKnifePayload::lastKnown,
            GlassKnifePayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
