package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: the frame the player's glove arm took as a
 * press held, let go or dropped, so the players watching draw the same
 * pose in place of the swing (decisions ability-json-names-its-arm-pose,
 * use-key-throw-swings-no-more).
 *
 * @param frame the frame the arm took, {@link ArmPoseKind#NONE} as it eases down
 */
public record GlovePosePayload(ArmPoseKind frame) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GlovePosePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "glove_pose"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, GlovePosePayload> STREAM_CODEC = StreamCodec.composite(
            ArmPoseKind.STREAM_CODEC, GlovePosePayload::frame,
            GlovePosePayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
