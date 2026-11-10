package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: the local player's client carried it through a
 * linked gate on the frame its eye crossed the face, and tells the server
 * where its feet stood, how it moved and where it looked as it crossed, so
 * the server sets the player where the client did.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 *
 * @param near   the gate crossed
 * @param feet   where the feet stood as the eye crossed
 * @param motion how the player moved
 * @param yaw    its yaw, in degrees
 * @param pitch  its pitch, in degrees
 */
public record GateCrossedPayload(Gate near, Vec3 feet, Vec3 motion, float yaw, float pitch)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GateCrossedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "gate_crossed"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, GateCrossedPayload> STREAM_CODEC = StreamCodec.composite(
            Gate.STREAM_CODEC, GateCrossedPayload::near,
            Vec3.STREAM_CODEC, GateCrossedPayload::feet,
            Vec3.STREAM_CODEC, GateCrossedPayload::motion,
            ByteBufCodecs.FLOAT, GateCrossedPayload::yaw,
            ByteBufCodecs.FLOAT, GateCrossedPayload::pitch,
            GateCrossedPayload::new);

    /**
     * Hands the crossing to the passage on the server thread.
     *
     * @param payload the payload
     * @param context the network context
     */
    public static void handle(GateCrossedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GatePassage.announced(player, payload.near(), payload.feet(), payload.motion(), payload.yaw(),
                        payload.pitch());
            }
        });
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
