package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a caster's bone shotel latched or dissolved, sent to the caster and the players
 * watching, so every client draws the server's shotel in the hand that cast it.
 * decision blade-forms-a-bone-shotel-in-hand
 *
 * @param casterId  the caster's entity id
 * @param standing  whether the shotel stands, false once it dissolves
 * @param rightHand whether it stands in the right hand
 */
public record BoneBladePayload(int casterId, boolean standing, boolean rightHand) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<BoneBladePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "bone_blade"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, BoneBladePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BoneBladePayload::casterId,
            ByteBufCodecs.BOOL, BoneBladePayload::standing,
            ByteBufCodecs.BOOL, BoneBladePayload::rightHand,
            BoneBladePayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
