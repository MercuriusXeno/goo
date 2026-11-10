package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.Unpooled;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Server-to-client payload: one chunk of a linked portal's far side, its
 * sections' block states and biomes and its sky and block light, streamed
 * to a player looking into the portal however far the partner stands, so
 * the client draws the view through without the chunk in its own ring.
 * linked-partner-chunks-stream-to-any-viewer
 *
 * @param x        the chunk's x
 * @param z        the chunk's z
 * @param sections every section, bottom up, as LevelChunkSection writes it
 * @param sky      each section's sky light, bottom up, empty where it holds none
 * @param block    each section's block light, bottom up, empty where it holds none
 */
public record PartnerChunkPayload(int x, int z, byte[] sections, List<byte[]> sky, List<byte[]> block)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PartnerChunkPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "partner_chunk"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, PartnerChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PartnerChunkPayload::x,
            ByteBufCodecs.VAR_INT, PartnerChunkPayload::z,
            ByteBufCodecs.BYTE_ARRAY, PartnerChunkPayload::sections,
            ByteBufCodecs.BYTE_ARRAY.apply(ByteBufCodecs.list()), PartnerChunkPayload::sky,
            ByteBufCodecs.BYTE_ARRAY.apply(ByteBufCodecs.list()), PartnerChunkPayload::block,
            PartnerChunkPayload::new);

    private static final byte[] NONE = new byte[0];
    private static final int HASH_PRIME = 31;

    /**
     * Packs a chunk the server holds.
     *
     * @param chunk the chunk
     * @param light the level's light engine
     * @return the payload
     */
    public static PartnerChunkPayload of(LevelChunk chunk, LevelLightEngine light) {
        List<byte[]> sky = new ArrayList<>();
        List<byte[]> block = new ArrayList<>();
        ChunkPos pos = chunk.getPos();
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
            SectionPos section = SectionPos.of(pos, chunk.getSectionYFromSectionIndex(index));
            sky.add(bytesOf(light.getLayerListener(LightLayer.SKY).getDataLayerData(section)));
            block.add(bytesOf(light.getLayerListener(LightLayer.BLOCK).getDataLayerData(section)));
        }
        return pack(pos, chunk.getSections(), sky, block);
    }

    /**
     * Packs a chunk's sections and light.
     *
     * @param pos      the chunk's position
     * @param sections its sections, bottom up
     * @param sky      each section's sky light, bottom up, empty where it holds none
     * @param block    each section's block light, bottom up, empty where it holds none
     * @return the payload
     */
    public static PartnerChunkPayload pack(ChunkPos pos, LevelChunkSection[] sections, List<byte[]> sky,
            List<byte[]> block) {
        FriendlyByteBuf written = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (LevelChunkSection section : sections) {
                section.write(written);
            }
            byte[] bytes = new byte[written.readableBytes()];
            written.readBytes(bytes);
            return new PartnerChunkPayload(pos.x(), pos.z(), bytes, List.copyOf(sky), List.copyOf(block));
        } finally {
            written.release();
        }
    }

    private static byte[] bytesOf(DataLayer layer) {
        return layer == null ? NONE : layer.getData().clone();
    }

    /**
     * @return a hash of the chunk's blocks and light, which changes when either does
     */
    public int contentsHash() {
        int hash = Arrays.hashCode(sections);
        for (byte[] layer : sky) {
            hash = HASH_PRIME * hash + Arrays.hashCode(layer);
        }
        for (byte[] layer : block) {
            hash = HASH_PRIME * hash + Arrays.hashCode(layer);
        }
        return hash;
    }

    /**
     * @return the chunk's position
     */
    public ChunkPos pos() {
        return new ChunkPos(x, z);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
