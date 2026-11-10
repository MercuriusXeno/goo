package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the server carried a body through a linked gate,
 * so each client watching it turns the body it draws through the same pair
 * and slides it on out of the partner, rather than sliding it from the gate
 * it went into through whatever stands between.
 * aim-and-shots-carry-through-linked-gates
 *
 * @param entityId the carried body's id
 * @param feet     where its feet came out
 * @param motion   how it moves coming out
 * @param yaw      its yaw coming out, in degrees
 * @param pitch    its pitch coming out, in degrees
 * @param entered  the gate it went into
 * @param exit     the partner it came out of
 */
public record GateCarriedPayload(int entityId, Vec3 feet, Vec3 motion, float yaw, float pitch, Gate entered,
        Gate exit) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GateCarriedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "gate_carried"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, GateCarriedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GateCarriedPayload::entityId,
            Vec3.STREAM_CODEC, GateCarriedPayload::feet,
            Vec3.STREAM_CODEC, GateCarriedPayload::motion,
            ByteBufCodecs.FLOAT, GateCarriedPayload::yaw,
            ByteBufCodecs.FLOAT, GateCarriedPayload::pitch,
            Gate.STREAM_CODEC, GateCarriedPayload::entered,
            Gate.STREAM_CODEC, GateCarriedPayload::exit,
            GateCarriedPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
