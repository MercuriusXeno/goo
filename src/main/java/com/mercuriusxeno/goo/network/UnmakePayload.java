package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: an unmake has dissolved this share of a block,
 * so the client draws the crucible's dissolve over it.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param pos      the block dissolving
 * @param fraction the share dissolved, from 0 whole to 1 gone
 */
public record UnmakePayload(BlockPos pos, float fraction) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<UnmakePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "unmake"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, UnmakePayload> STREAM_CODEC =
        StreamCodec.of(UnmakePayload::encode, UnmakePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, UnmakePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeFloat(payload.fraction);
    }

    private static UnmakePayload decode(FriendlyByteBuf buf) {
        return new UnmakePayload(buf.readBlockPos(), buf.readFloat());
    }
}
