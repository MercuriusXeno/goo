package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: the cells a Blast broke, each with the block
 * that stood there, so each client throws that block's own break shards
 * where it stood. The states travel here because the cells read air by the
 * time the client draws.
 * blast-broken-blocks-throw-their-own-break-shards
 *
 * @param cells the broken cells, in the order the explosion broke them
 */
public record BlastShardsPayload(List<Shattered> cells) implements CustomPacketPayload {

    /**
     * One broken cell and the block that stood in it.
     *
     * @param pos   the cell
     * @param state the block state that stood there before the break
     */
    public record Shattered(BlockPos pos, BlockState state) {

        private static final StreamCodec<ByteBuf, Shattered> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Shattered::pos,
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), Shattered::state,
                Shattered::new);
    }

    /** Payload type ID for registration. */
    public static final Type<BlastShardsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "blast_shards"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, BlastShardsPayload> STREAM_CODEC =
            Shattered.STREAM_CODEC.apply(ByteBufCodecs.list()).map(BlastShardsPayload::new, BlastShardsPayload::cells);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
