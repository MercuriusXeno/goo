package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the pose a player's glove arm takes now, for
 * every client drawing that player (decision shards-sling-then-morph-to-flechettes).
 *
 * @param entityId   the posed player's entity id
 * @param kind       the pose
 * @param gloveRight whether the glove is in the player's right hand
 */
public record ArmPosePayload(int entityId, ArmPoseKind kind, boolean gloveRight) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ArmPosePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "arm_pose"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ArmPosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArmPosePayload::entityId,
            ByteBufCodecs.idMapper(index -> ArmPoseKind.values()[index], ArmPoseKind::ordinal), ArmPosePayload::kind,
            ByteBufCodecs.BOOL, ArmPosePayload::gloveRight,
            ArmPosePayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
