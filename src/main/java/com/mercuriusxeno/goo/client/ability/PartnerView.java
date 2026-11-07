package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.PartnerChunkPayload;
import com.mercuriusxeno.goo.network.PartnerChunksDropPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The second level view beside the ClientLevel: the far side of a linked
 * pair the server streamed while the local player looks into it, held apart
 * from the ClientLevel's chunk cache and its view-distance ring, read as a
 * level by the meshes the portal pass draws from it.
 * linked-partner-chunks-stream-to-any-viewer
 */
public final class PartnerView implements BlockAndTintGetter {

    /** The view the client holds. */
    public static final PartnerView CLIENT = new PartnerView();

    /** Where the portal pass draws a partner's side from. */
    public enum Source {
        /** The ClientLevel holds the partner's chunk. */
        CLIENT_LEVEL,
        /** The streamed view holds it, the ClientLevel not. */
        STREAMED,
        /** Neither holds it, so no view draws. */
        NONE
    }

    /**
     * One streamed chunk.
     *
     * @param sections its sections, bottom up
     * @param sky      each section's sky light, null where it holds none
     * @param block    each section's block light, null where it holds none
     */
    record Held(LevelChunkSection[] sections, DataLayer[] sky, DataLayer[] block) {
    }

    private static final int FULL_SKY = 15;
    /** The tint a block takes where the view holds nothing: none. */
    private static final int NO_TINT = -1;

    private final Map<ChunkPos, Held> chunks = new HashMap<>();
    private int minSectionY;
    private int sectionCount;
    private long version;

    /**
     * Where the portal pass draws a partner's side from: the ClientLevel when
     * it holds the partner's chunk, else the streamed view when it does.
     *
     * @param clientLevelHolds whether the ClientLevel holds the partner's chunk
     * @param streamedHolds    whether the streamed view holds it
     * @return the source
     */
    public static Source sourceFor(boolean clientLevelHolds, boolean streamedHolds) {
        if (clientLevelHolds) {
            return Source.CLIENT_LEVEL;
        }
        return streamedHolds ? Source.STREAMED : Source.NONE;
    }

    /**
     * Holds a streamed chunk, replacing any held there before.
     *
     * @param payload       the chunk
     * @param factory       the containers its sections read into
     * @param minSectionY   the level's lowest section
     * @param sectionCount  how many sections a chunk stacks
     */
    public void hold(PartnerChunkPayload payload, PalettedContainerFactory factory, int minSectionY,
            int sectionCount) {
        this.minSectionY = minSectionY;
        this.sectionCount = sectionCount;
        FriendlyByteBuf read = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.sections()));
        LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
        DataLayer[] sky = new DataLayer[sectionCount];
        DataLayer[] block = new DataLayer[sectionCount];
        for (int index = 0; index < sectionCount; index++) {
            sections[index] = new LevelChunkSection(factory);
            sections[index].read(read);
            sky[index] = layerOf(payload.sky(), index);
            block[index] = layerOf(payload.block(), index);
        }
        chunks.put(payload.pos(), new Held(sections, sky, block));
        version++;
    }

    private static @Nullable DataLayer layerOf(List<byte[]> layers, int index) {
        return index < layers.size() && layers.get(index).length == DataLayer.SIZE
                ? new DataLayer(layers.get(index).clone()) : null;
    }

    /**
     * Drops a streamed chunk.
     *
     * @param pos the chunk
     */
    public void drop(ChunkPos pos) {
        if (chunks.remove(pos) != null) {
            version++;
        }
    }

    /**
     * Drops every streamed chunk.
     */
    public void clear() {
        chunks.clear();
        version++;
    }

    /**
     * @param pos a chunk
     * @return whether the view holds it
     */
    public boolean holds(ChunkPos pos) {
        return chunks.containsKey(pos);
    }

    /**
     * @return every chunk the view holds
     */
    public Set<ChunkPos> held() {
        return Set.copyOf(chunks.keySet());
    }

    /**
     * @return a count bumped by every hold and drop, which the meshes rebuild against
     */
    public long version() {
        return version;
    }

    /**
     * @param pos a chunk
     * @return its sections, bottom up, or none where it is not held
     */
    LevelChunkSection[] sectionsOf(ChunkPos pos) {
        Held held = chunks.get(pos);
        return held == null ? new LevelChunkSection[0] : held.sections();
    }

    /**
     * @return the lowest section a held chunk stacks from
     */
    int minSectionY() {
        return minSectionY;
    }

    private @Nullable Held heldAt(BlockPos pos) {
        Held held = chunks.get(ChunkPos.containing(pos));
        int index = SectionPos.blockToSectionCoord(pos.getY()) - minSectionY;
        return held == null || index < 0 || index >= sectionCount ? null : held;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        Held held = heldAt(pos);
        if (held == null) {
            return Blocks.AIR.defaultBlockState();
        }
        int index = SectionPos.blockToSectionCoord(pos.getY()) - minSectionY;
        return held.sections()[index].getBlockState(SectionPos.sectionRelative(pos.getX()),
                SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ()));
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        Held held = heldAt(pos);
        if (held == null) {
            return layer == LightLayer.SKY ? FULL_SKY : 0;
        }
        int index = SectionPos.blockToSectionCoord(pos.getY()) - minSectionY;
        DataLayer data = layer == LightLayer.SKY ? held.sky()[index] : held.block()[index];
        if (data == null) {
            return layer == LightLayer.SKY ? FULL_SKY : 0;
        }
        return data.get(SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()),
                SectionPos.sectionRelative(pos.getZ()));
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return Math.max(getBrightness(LightLayer.BLOCK, pos), getBrightness(LightLayer.SKY, pos) - darkening);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? CardinalLighting.DEFAULT : mc.level.cardinalLighting();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        Held held = heldAt(pos);
        if (held == null) {
            return NO_TINT;
        }
        int index = SectionPos.blockToSectionCoord(pos.getY()) - minSectionY;
        return color.getColor(held.sections()[index].getNoiseBiome(QuartPos.fromBlock(SectionPos.sectionRelative(
                pos.getX())), QuartPos.fromBlock(SectionPos.sectionRelative(pos.getY())),
                QuartPos.fromBlock(SectionPos.sectionRelative(pos.getZ()))).value(), pos.getX(), pos.getZ());
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return sectionCount * SectionPos.SECTION_SIZE;
    }

    @Override
    public int getMinY() {
        return SectionPos.sectionToBlockCoord(minSectionY);
    }

    /**
     * Holds a chunk the server streamed into the client's view.
     *
     * @param payload the chunk
     * @param context the network context
     */
    public static void handle(PartnerChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                CLIENT.hold(payload, PalettedContainerFactory.create(mc.level.registryAccess()),
                        mc.level.getMinSectionY(), mc.level.getSectionsCount());
            }
        });
    }

    /**
     * Drops the chunks the local player's look left.
     *
     * @param payload the chunks
     * @param context the network context
     */
    public static void handleDrop(PartnerChunksDropPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> payload.positions().forEach(CLIENT::drop));
    }
}
