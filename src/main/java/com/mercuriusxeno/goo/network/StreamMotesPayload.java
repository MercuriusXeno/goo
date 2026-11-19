package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.ConvergingCone;
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
 * @param coneShape   the cone the motes fan across, its widths across and up and down, apex to rim, in degrees,
 *                    a sprayer's nozzle's shape for its drips
 * @param speed       each mote's launch speed along its heading, blocks per tick, for motes off the arc
 * @param throwSpeed  the speed the stream throws its drips at along the cone's fan, each flying the arc the
 *                    server's drip flies; {@link com.mercuriusxeno.goo.ability.Delivery#NO_THROW_SPEED} for motes
 *                    off the arc
 * @param strikesLiving whether a drawn drip ends on a living thing it meets, as the server's drip does
 * @param held        the hold's tick the drips were thrown on, which seeds their places in the cone
 * @param drips       the drips the server threw this tick, each drawn on its own arc; 0 for motes off the arc
 * @param eye         the caster's eye the drips' cone centers from, the apex for motes off the arc
 * @param lands       how far from the eye the drips' cone lands, in blocks; 0 for motes off the arc
 */
public record StreamMotesPayload(int casterId, Vec3 apex, Vec3 axis, Identifier particle, ConeShape coneShape,
                                 double speed, double throwSpeed, boolean strikesLiving, int held, int drips,
                                 Vec3 eye, double lands)
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
            ConeShape.STREAM_CODEC, StreamMotesPayload::coneShape,
            ByteBufCodecs.DOUBLE, StreamMotesPayload::speed,
            ByteBufCodecs.DOUBLE, StreamMotesPayload::throwSpeed,
            ByteBufCodecs.BOOL, StreamMotesPayload::strikesLiving,
            ByteBufCodecs.VAR_INT, StreamMotesPayload::held,
            ByteBufCodecs.VAR_INT, StreamMotesPayload::drips,
            Vec3.STREAM_CODEC, StreamMotesPayload::eye,
            ByteBufCodecs.DOUBLE, StreamMotesPayload::lands,
            StreamMotesPayload::new);

    /**
     * The cone the server's drips this tick converge on, which the drawn drips converge on too.
     * residual-spray-cone-centers-on-the-look-from-the-blob
     *
     * @return the cone
     */
    public ConvergingCone cone() {
        return new ConvergingCone(eye, axis, lands);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
