package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: how far a block has gone toward the state it
 * calcifies into, which the client draws as that state mingled in by the
 * share; a zero share clears it (decision petrify-stone-encasement-and-calcify-map).
 *
 * @param pos     the block
 * @param toward  the state it calcifies into, by its block state id
 * @param share   the share built, 0 to 1
 * @param tint    the RGB the client tints the mingled block by, {@link #UNTINTED} for none
 */
public record BlockExposurePayload(BlockPos pos, int toward, float share, int tint) implements CustomPacketPayload {

    /** The tint of a mingled block drawn in its own colors. */
    public static final int UNTINTED = -1;

    /** Payload type ID for registration. */
    public static final Type<BlockExposurePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "block_exposure"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, BlockExposurePayload> STREAM_CODEC =
            StreamCodec.of(BlockExposurePayload::encode, BlockExposurePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, BlockExposurePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.toward);
        buf.writeFloat(payload.share);
        buf.writeInt(payload.tint);
    }

    private static BlockExposurePayload decode(FriendlyByteBuf buf) {
        return new BlockExposurePayload(buf.readBlockPos(), buf.readVarInt(), buf.readFloat(), buf.readInt());
    }
}
