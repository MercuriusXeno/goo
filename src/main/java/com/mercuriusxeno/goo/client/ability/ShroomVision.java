package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Sculker's shroom vision: while Sculker stands, every fungus block within reach, a block whose block item's goo value
 * holds sculk (FungusBlocks), is in what the sense view draws at any noise level, with no echo needed to reveal it,
 * beside the blocks the echoes lit. The blocks are scanned again once a second, chunk section by chunk section,
 * skipping any section whose palette holds none, and the nearest are kept up to a cap.
 * decision sculker-absorbs-sight-as-shroom-vision
 */
public final class ShroomVision {

    /** How far the vision reaches, in blocks, as Transmit's reach did under Sight. */
    static final double VISION_REACH = 64;
    static final int RESCAN_TICKS = 20;
    private static final int MOST_SEEN = 512;
    private static final int SECTION_SIZE = SectionPos.SECTION_SIZE;
    /** Marks that no scan has run. */
    static final long UNSCANNED = Long.MIN_VALUE;

    private static final List<BlockPos> SEEN = new ArrayList<>();
    private static long scannedAt = UNSCANNED;

    private ShroomVision() {
    }

    /**
     * The blocks Sculker's sense view draws and how strongly: every block an echo lit, at its echo's strength, and
     * every block of the shroom network nearby at the vision's own presence, whatever the echoes heard.
     *
     * @param echoLit   each block an echo lit and its strength
     * @param nearby    the blocks within the vision's reach and what stands there
     * @param inNetwork whether a block belongs to the shroom network
     * @param presence  how fully the vision stands, zero to one
     * @return each block drawn and its strength
     */
    static Map<BlockPos, Float> drawn(Map<BlockPos, Float> echoLit, Map<BlockPos, BlockState> nearby,
                                      Predicate<BlockState> inNetwork, float presence) {
        Map<BlockPos, Float> drawn = new HashMap<>(echoLit);
        nearby.forEach((pos, state) -> {
            if (inNetwork.test(state)) {
                drawn.merge(pos, presence, Math::max);
            }
        });
        return drawn;
    }

    /**
     * The shroom network blocks within the vision's reach of the local player and what stands there now, the
     * scan run again once a second.
     *
     * @param mc     the client, its player and level loaded
     * @param now    the game time
     * @param fungus the fungus blocks
     * @return each block nearby and its state
     */
    static Map<BlockPos, BlockState> nearby(Minecraft mc, long now, Set<Block> fungus) {
        if (isDue(scannedAt, now)) {
            rescan(mc.level, mc.player.getEyePosition(), fungus);
            scannedAt = now;
        }
        Map<BlockPos, BlockState> nearby = new HashMap<>();
        for (BlockPos pos : SEEN) {
            nearby.put(pos, mc.level.getBlockState(pos));
        }
        return nearby;
    }

    /** Forgets the last scan, so the next vision scans afresh. */
    static void forget() {
        SEEN.clear();
        scannedAt = UNSCANNED;
    }

    /**
     * Whether a scan is due: none has run yet, a second has passed since the last, or the clock ran back past it.
     *
     * @param lastScan the game time of the last scan, or {@link #UNSCANNED}
     * @param now      the game time
     * @return true when the network should be scanned again
     */
    static boolean isDue(long lastScan, long now) {
        return lastScan == UNSCANNED || now < lastScan || now - lastScan >= RESCAN_TICKS;
    }

    private static void rescan(Level level, Vec3 eye, Set<Block> fungus) {
        SEEN.clear();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(VISION_REACH, VISION_REACH, VISION_REACH)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(VISION_REACH, VISION_REACH, VISION_REACH)));
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                ChunkAccess chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    scanChunk(chunk, low.y(), high.y(), eye, fungus);
                }
            }
        }
        SEEN.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        if (SEEN.size() > MOST_SEEN) {
            SEEN.subList(MOST_SEEN, SEEN.size()).clear();
        }
    }

    private static void scanChunk(ChunkAccess chunk, int lowY, int highY, Vec3 eye, Set<Block> fungus) {
        for (int sy = Math.max(lowY, chunk.getMinSectionY()); sy <= Math.min(highY, chunk.getMaxSectionY()); sy++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> fungus.contains(state.getBlock()))) {
                continue;
            }
            BlockPos base = new BlockPos(chunk.getPos().getMinBlockX(), SectionPos.sectionToBlockCoord(sy),
                    chunk.getPos().getMinBlockZ());
            scanSection(section, base, eye, fungus);
        }
    }

    private static void scanSection(LevelChunkSection section, BlockPos base, Vec3 eye, Set<Block> fungus) {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    if (fungus.contains(section.getBlockState(x, y, z).getBlock())) {
                        BlockPos pos = base.offset(x, y, z);
                        if (Vec3.atCenterOf(pos).distanceTo(eye) <= VISION_REACH) {
                            SEEN.add(pos);
                        }
                    }
                }
            }
        }
    }
}
