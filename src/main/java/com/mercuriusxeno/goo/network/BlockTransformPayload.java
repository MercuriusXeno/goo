package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a block transformed from one state to another,
 * which the client draws as the old block mingling into the new over its
 * shape and every face (decision petrify-stone-encasement-and-calcify-map).
 *
 * @param pos       the block transformed
 * @param fromState the state it was, by its block state id
 * @param toState   the state it became, by its block state id
 */
public record BlockTransformPayload(BlockPos pos, int fromState, int toState) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<BlockTransformPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "block_transform"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, BlockTransformPayload> STREAM_CODEC =
            StreamCodec.of(BlockTransformPayload::encode, BlockTransformPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Sends the transform to every player tracking its chunk; a listener that
     * never negotiated the mod's channels, a gametest's mock player, gets none.
     *
     * @param level the server level the block stands in
     */
    public void sendToTracking(ServerLevel level) {
        ChunkWatchers.send(level, pos, this);
    }

    private static void encode(FriendlyByteBuf buf, BlockTransformPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.fromState);
        buf.writeVarInt(payload.toState);
    }

    private static BlockTransformPayload decode(FriendlyByteBuf buf) {
        return new BlockTransformPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }
}
