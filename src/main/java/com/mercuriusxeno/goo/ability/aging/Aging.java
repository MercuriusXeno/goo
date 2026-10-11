package com.mercuriusxeno.goo.ability.aging;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BlockBreakHost;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Yore's aging on the server: a blob on a block the aging table names marks
 * it in its chunk, the chunk counts the in-game ticks it stands loaded, and
 * the block becomes its row's result once the row's ticks have passed. The
 * marks save with the chunk, so an aging resumes where it stood when the
 * chunk loads again.
 * old-blob-ages-valuables-slowly
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class Aging {

    /** Ticks between one aging pass and the next; each pass ages every loaded mark by as many. */
    static final int AGING_STEP = 20;
    private static final int FIZZLE_SMOKE = 12;
    private static final double FIZZLE_SPREAD = 0.3;
    private static final double FIZZLE_SPEED = 0.02;
    private static final double BLOCK_CENTER = 0.5;
    private static final float FIZZLE_VOLUME = 0.5f;
    private static final float FIZZLE_PITCH = 1.8f;

    /** The loaded chunks holding marks, per level, which each aging pass visits. */
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> MARKED = new HashMap<>();

    private Aging() {
    }

    /**
     * Starts the aging of a block the table names; a block it names not is
     * left alone.
     *
     * @param level the level
     * @param pos   the block's position
     * @return true when the block started aging
     */
    public static boolean start(ServerLevel level, BlockPos pos) {
        Optional<AgingEntry> entry = AgingTable.entryFor(level.getBlockState(pos));
        if (entry.isEmpty()) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.setData(GooAttachments.AGING_BLOCKS, marksOf(chunk).starting(pos, entry.get().source()));
        chunk.markUnsaved();
        MARKED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(chunk.getPos());
        return true;
    }

    /**
     * Shows a refused aging throw where it was aimed: a puff of smoke and a
     * quiet hiss.
     *
     * @param level the level
     * @param pos   the aimed block
     */
    public static void fizzle(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + BLOCK_CENTER;
        double y = pos.getY() + 1;
        double z = pos.getZ() + BLOCK_CENTER;
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, FIZZLE_SMOKE, FIZZLE_SPREAD, FIZZLE_SPREAD,
                FIZZLE_SPREAD, FIZZLE_SPEED);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, FIZZLE_VOLUME, FIZZLE_PITCH);
    }

    /**
     * Ages every mark in the level's loaded chunks by some ticks and turns
     * each block whose row's ticks have passed into its result; a mark whose
     * block changed, or whose row the table no longer holds, is dropped.
     *
     * @param level the level
     * @param ticks the ticks to age
     */
    public static void ageLoaded(ServerLevel level, int ticks) {
        Set<ChunkPos> marked = MARKED.getOrDefault(level.dimension(), Set.of());
        for (ChunkPos pos : List.copyOf(marked)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) {
                marked.remove(pos);
                continue;
            }
            AgingBlocks aged = marksOf(chunk).aged(ticks);
            List<AgingBlocks.Aging> settled = aged.aging().stream().filter(mark -> settle(level, mark)).toList();
            AgingBlocks left = aged.without(settled);
            chunk.setData(GooAttachments.AGING_BLOCKS, left);
            chunk.markUnsaved();
            if (left.isEmpty()) {
                marked.remove(pos);
            }
        }
    }

    /**
     * Whether a mark is done with: its block changed or its row is gone, or
     * its row's ticks have passed and the block has become its result.
     *
     * @param level the level
     * @param mark  the aging block
     * @return true when the mark is done with
     */
    private static boolean settle(ServerLevel level, AgingBlocks.Aging mark) {
        BlockState standing = level.getBlockState(mark.pos());
        Optional<AgingEntry> entry = AgingTable.entryFor(mark.source());
        if (entry.isEmpty() || !BuiltInRegistries.BLOCK.getKey(standing.getBlock()).equals(mark.source())) {
            return true;
        }
        if (mark.elapsed() < entry.get().ticks()) {
            return false;
        }
        BlockState result = BuiltInRegistries.BLOCK.getValue(entry.get().result()).defaultBlockState();
        BlockBreakHost.transform(level, mark.pos(), result);
        entry.get().drop().ifPresent(drop ->
                Block.popResource(level, mark.pos(), new ItemStack(BuiltInRegistries.ITEM.getValue(drop))));
        return true;
    }

    private static AgingBlocks marksOf(ChunkAccess chunk) {
        return chunk.hasData(GooAttachments.AGING_BLOCKS) ? chunk.getData(GooAttachments.AGING_BLOCKS) : AgingBlocks.NONE;
    }

    /**
     * Lists a loading chunk that holds marks, so the aging passes visit it.
     *
     * @param event the chunk load event
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            noteLoaded(level, event.getChunk());
        }
    }

    /**
     * Lists a chunk that has loaded holding marks.
     *
     * @param level the chunk's level
     * @param chunk the chunk
     */
    public static void noteLoaded(ServerLevel level, ChunkAccess chunk) {
        if (!marksOf(chunk).isEmpty()) {
            MARKED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(chunk.getPos());
        }
    }

    /**
     * Drops an unloading chunk from the aging passes; its marks save with it.
     *
     * @param event the chunk unload event
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MARKED.getOrDefault(level.dimension(), new HashSet<>()).remove(event.getChunk().getPos());
        }
    }

    /**
     * Runs an aging pass every {@link #AGING_STEP} ticks.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % AGING_STEP == 0) {
            ageLoaded(level, AGING_STEP);
        }
    }

    /** Forgets every listed chunk, as a server stop does. */
    public static void clear() {
        MARKED.clear();
    }
}
