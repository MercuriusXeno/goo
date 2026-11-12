package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Server-to-client payload: one tick of a glove stream's motes, so each
 * watching client launches them from the glove fanned across the cone,
 * naming the caster so every mote swings with its own caster's glove over
 * its opening ticks. Sent to the players watching the caster and to the
 * caster itself.
 * glove-stream-motes-swing-with-the-glove-at-launch
 * goop-and-slick-spray-follows-the-coat
 *
 * @param casterId    the spraying player
 * @param apex        the cone's apex, the glove the motes leave
 * @param axis        the cone's axis
 * @param particle    the stream's particle id
 * @param coneDegrees the cone the motes fan across, apex to rim, in degrees
 * @param speed       each mote's launch speed along its heading, blocks per tick, for motes off the coat
 * @param travel      the caster's travel velocity, blocks per tick, which each mote launches with
 * @param coat        the coat the motes fly onto in place of their heading and speed, empty for a stream
 *                    whose spray does not follow its coat
 */
public record StreamMotesPayload(int casterId, Vec3 apex, Vec3 axis, Identifier particle, double coneDegrees,
                                 double speed, Vec3 travel, Optional<CoatSpray> coat) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<StreamMotesPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "stream_motes"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, StreamMotesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StreamMotesPayload::casterId,
            Vec3.STREAM_CODEC, StreamMotesPayload::apex,
            Vec3.STREAM_CODEC, StreamMotesPayload::axis,
            Identifier.STREAM_CODEC, StreamMotesPayload::particle,
            ByteBufCodecs.DOUBLE, StreamMotesPayload::coneDegrees,
            ByteBufCodecs.DOUBLE, StreamMotesPayload::speed,
            Vec3.STREAM_CODEC, StreamMotesPayload::travel,
            ByteBufCodecs.optional(CoatSpray.STREAM_CODEC), StreamMotesPayload::coat,
            StreamMotesPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
