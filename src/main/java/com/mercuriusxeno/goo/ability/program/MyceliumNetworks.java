package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Each level's stored mycelium network, and what keeps it current. A server
 * chunk's network is saved with the chunk as an attachment and read back
 * into the cache; a chunk asked for with no saved network is scanned once,
 * skipping every section whose palette holds no mycelial block. A block
 * write that makes a place mycelial or stops it being so, placed, spread,
 * broken or a Creep cell come or gone, drops the chunk's saved and cached
 * network, so the next question reads it again; a chunk that loads or
 * unloads drops its cached one.
 * mycelium-networks-are-cached-dags-joined-by-chunk-connectors
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class MyceliumNetworks {

    private static final int SECTION_SPAN = 16;
    private static final Map<Level, MyceliumNetwork> BY_LEVEL = Collections.synchronizedMap(new IdentityHashMap<>());

    private MyceliumNetworks() {
    }

    /**
     * A level's stored mycelium network.
     *
     * @param level the level
     * @return its network
     */
    public static MyceliumNetwork of(Level level) {
        return BY_LEVEL.computeIfAbsent(level, held -> new MyceliumNetwork(chunk -> read(held, chunk)));
    }

    /**
     * Drops a chunk's saved and cached network where a block write is about
     * to make a place mycelial or stop it being so; called by LevelChunk's
     * mixin before each write.
     *
     * @param chunk the chunk written
     * @param pos   the place
     * @param now   the block being written
     */
    public static void blockChanging(LevelChunk chunk, BlockPos pos, BlockState now) {
        if (MycelialBlocks.is(chunk.getBlockState(pos)) == MycelialBlocks.is(now)) {
            return;
        }
        MyceliumNetwork network = BY_LEVEL.get(chunk.getLevel());
        if (network != null) {
            network.forget(chunk.getPos());
        }
        if (chunk.hasData(GooAttachments.MYCELIUM_NETWORK)) {
            chunk.removeData(GooAttachments.MYCELIUM_NETWORK);
        }
    }

    /**
     * Drops a loaded chunk's cached network, since a client's chunk takes its
     * blocks from the server without a block write.
     *
     * @param event the load
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        forget(event);
    }

    /**
     * Drops an unloaded chunk's cached network.
     *
     * @param event the unload
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        forget(event);
    }

    /**
     * Drops an unloaded level's network.
     *
     * @param event the unload
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        BY_LEVEL.remove(event.getLevel());
    }

    private static void forget(ChunkEvent event) {
        MyceliumNetwork network = BY_LEVEL.get(event.getLevel());
        if (network != null) {
            network.forget(event.getChunk().getPos());
        }
    }

    /**
     * A chunk's network: the one saved with it, or else its blocks scanned
     * and, on the server, saved with it.
     *
     * @param level the level
     * @param pos   the chunk
     * @return its network, or null where the chunk stands unloaded
     */
    private static @Nullable ChunkMycelium read(Level level, ChunkPos pos) {
        if (!level.hasChunk(pos.x(), pos.z())) {
            return null;
        }
        LevelChunk chunk = level.getChunk(pos.x(), pos.z());
        if (chunk.hasData(GooAttachments.MYCELIUM_NETWORK)) {
            return chunk.getData(GooAttachments.MYCELIUM_NETWORK);
        }
        ChunkMycelium network = ChunkMycelium.of(mycelialBlocks(chunk));
        if (!level.isClientSide()) {
            chunk.setData(GooAttachments.MYCELIUM_NETWORK, network);
        }
        return network;
    }

    private static List<BlockPos> mycelialBlocks(LevelChunk chunk) {
        List<BlockPos> found = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.hasOnlyAir() && section.maybeHas(MycelialBlocks::is)) {
                BlockPos corner = chunk.getPos().getBlockAt(0,
                        SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index)), 0);
                scanSection(section, corner, found);
            }
        }
        return found;
    }

    private static void scanSection(LevelChunkSection section, BlockPos corner, List<BlockPos> found) {
        for (int y = 0; y < SECTION_SPAN; y++) {
            for (int z = 0; z < SECTION_SPAN; z++) {
                for (int x = 0; x < SECTION_SPAN; x++) {
                    if (MycelialBlocks.is(section.getBlockState(x, y, z))) {
                        found.add(corner.offset(x, y, z));
                    }
                }
            }
        }
    }
}
