package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one tick of Decay's swarm, so the client
 * launches gnats from the glove fanned across the stream's cone around the
 * aim, each pulled every tick toward the mob the swarm chases, or with none,
 * toward its own point in the box the crosshair lands on. Sent to the
 * players watching the caster and to the caster itself.
 * decay-gnats-degrade-each-block-once
 *
 * @param casterId    the streaming player
 * @param glove       the glove hand the stream left from
 * @param aim         the heading from the glove onto the crosshair
 * @param sprayDegrees the cone the gnats fan across, apex to rim, in degrees
 * @param preyId      the mob the swarm chases, or {@link #NO_PREY}
 * @param targetMin   the low corner of the box the gnats converge on with no prey
 * @param targetMax   the high corner of that box
 */
public record GnatSeekPayload(int casterId, Vec3 glove, Vec3 aim, double sprayDegrees, int preyId, Vec3 targetMin,
                              Vec3 targetMax) implements CustomPacketPayload {

    /** The prey id of a tick whose swarm chases no mob. */
    public static final int NO_PREY = -1;

    /** Payload type ID for registration. */
    public static final Type<GnatSeekPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "gnat_seek"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GnatSeekPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GnatSeekPayload::casterId,
            Vec3.STREAM_CODEC, GnatSeekPayload::glove,
            Vec3.STREAM_CODEC, GnatSeekPayload::aim,
            ByteBufCodecs.DOUBLE, GnatSeekPayload::sprayDegrees,
            ByteBufCodecs.VAR_INT, GnatSeekPayload::preyId,
            Vec3.STREAM_CODEC, GnatSeekPayload::targetMin,
            Vec3.STREAM_CODEC, GnatSeekPayload::targetMax,
            GnatSeekPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
