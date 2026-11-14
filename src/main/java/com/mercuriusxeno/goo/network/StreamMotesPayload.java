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
 * Server-to-client payload: one tick of a glove stream's motes, so each
 * watching client launches them from the glove fanned across the cone,
 * naming the caster so every mote swings with its own caster's glove over
 * its opening ticks. Sent to the players watching the caster and to the
 * caster itself.
 * glove-stream-motes-swing-with-the-glove-at-launch
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-wobbles-a-mob-its-drip-strikes
 *
 * @param casterId    the spraying player
 * @param apex        the cone's apex, the glove the motes leave
 * @param axis        the cone's axis
 * @param particle    the stream's particle id
 * @param coneDegrees the cone the motes fan across, apex to rim, in degrees
 * @param speed       each mote's launch speed along its heading, blocks per tick, for motes off the arc
 * @param throwSpeed  the speed the stream throws its drips at along the cone's fan, each flying the arc the
 *                    server's drip flies; {@link com.mercuriusxeno.goo.ability.Delivery#NO_THROW_SPEED} for motes
 *                    off the arc
 * @param strikesLiving whether a drawn drip ends on a living thing it meets, as the server's drip does
 */
public record StreamMotesPayload(int casterId, Vec3 apex, Vec3 axis, Identifier particle, double coneDegrees,
                                 double speed, double throwSpeed, boolean strikesLiving)
        implements CustomPacketPayload {

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
            ByteBufCodecs.DOUBLE, StreamMotesPayload::throwSpeed,
            ByteBufCodecs.BOOL, StreamMotesPayload::strikesLiving,
            StreamMotesPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
