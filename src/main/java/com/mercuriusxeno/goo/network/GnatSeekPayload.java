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
 * Server-to-client payload: one tick of Decay's swarm bit this mob, so the
 * client launches gnats from the glove along the aim, each steering every
 * tick toward the mob where it stands then. Sent to the players watching the
 * caster and to the caster itself.
 * decay-gnats-degrade-each-block-once
 *
 * @param casterId the streaming player
 * @param glove    the glove hand the stream left from
 * @param aim      the heading from the glove onto the crosshair
 * @param preyId   the mob the swarm bites
 */
public record GnatSeekPayload(int casterId, Vec3 glove, Vec3 aim, int preyId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GnatSeekPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "gnat_seek"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GnatSeekPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GnatSeekPayload::casterId,
            Vec3.STREAM_CODEC, GnatSeekPayload::glove,
            Vec3.STREAM_CODEC, GnatSeekPayload::aim,
            ByteBufCodecs.VAR_INT, GnatSeekPayload::preyId,
            GnatSeekPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
