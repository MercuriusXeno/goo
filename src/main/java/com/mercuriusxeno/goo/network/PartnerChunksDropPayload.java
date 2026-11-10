package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: the streamed far-side chunks a player's look
 * left, which its client drops.
 * linked-partner-chunks-stream-to-any-viewer
 *
 * @param chunks the chunks, each as ChunkPos packs it
 */
public record PartnerChunksDropPayload(List<Long> chunks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PartnerChunksDropPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "partner_chunks_drop"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PartnerChunksDropPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), PartnerChunksDropPayload::chunks,
            PartnerChunksDropPayload::new);

    /**
     * @return the chunks dropped
     */
    public List<ChunkPos> positions() {
        return chunks.stream().map(ChunkPos::unpack).toList();
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
