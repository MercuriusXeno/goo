package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The Shatter cells this client draws shards over, held in client memory
 * only, since the cell is a plain block with no block entity and no chunk
 * storage: the chunk sections near the camera are scanned again every few
 * ticks, skipping any whose palette holds no Shatter cell, and the nearest
 * cells are kept. A cell found in a section an earlier scan already read
 * landed since, so its shards fade in from then; a cell found in a section
 * read for the first time stood there already, so its shards stand at rest.
 * Each cell's shard layout is drawn from a seed the client picks the first
 * time it sees the cell.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 * cloud-seed-per-client-ephemeral
 */
final class ShatterCells {

    /** The landing time of a cell that stood before this client first read its section: long since at rest. */
    static final long LONG_LANDED = Long.MIN_VALUE / 2;
    /** Ticks between scans. */
    static final int SCAN_TICKS = 4;
    /** How far from the camera cells are scanned, in blocks. */
    static final double REACH = 24.0;
    /** The most cells drawn, the nearest kept, since each shard face casts its own reflection ray. */
    static final int MOST_DRAWN = 64;
    private static final int SECTION_SIZE = 16;

    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final Set<Long> readSections = new HashSet<>();
    private final Set<BlockPos> foundLastScan = new HashSet<>();
    private final LongSupplier seeds;
    private long lastScan = Long.MIN_VALUE;

    /**
     * One cell's shards: when its goo landed and its layout.
     *
     * @param landedAt the game tick the cell's goo landed, or {@link #LONG_LANDED}
     * @param layout   the cell's shard layout
     */
    record Cell(long landedAt, ShardTable layout) {
    }

    /**
     * An index seeding each cell's layout from a fresh random source.
     */
    ShatterCells() {
        this(new Random()::nextLong);
    }

    /**
     * An index seeding each cell's layout from the given source.
     *
     * @param seeds answers the seed of each new cell's layout
     */
    ShatterCells(LongSupplier seeds) {
        this.seeds = seeds;
    }

    /**
     * When a cell a scan finds landed: now where the last scan read its
     * section and found no cell there, long since where the last scan found
     * it standing or this scan reads its section for the first time.
     *
     * @param sectionReadBefore whether the last scan read the cell's section
     * @param foundBefore       whether the last scan found the cell standing
     * @param now               the game tick of this scan
     * @return the cell's landing tick
     */
    static long landedAt(boolean sectionReadBefore, boolean foundBefore, long now) {
        return sectionReadBefore && !foundBefore ? now : LONG_LANDED;
    }

    /**
     * The cells drawn, by position.
     *
     * @return the cells
     */
    Map<BlockPos, Cell> cells() {
        return cells;
    }

    /**
     * Forgets every cell, as the client leaves the level.
     */
    void clear() {
        cells.clear();
        readSections.clear();
        foundLastScan.clear();
        lastScan = Long.MIN_VALUE;
    }

    /**
     * Scans the sections near the camera once its scan is due.
     *
     * @param level the client level
     * @param eye   the camera's position
     * @param now   the game tick
     */
    void rescanIfDue(Level level, Vec3 eye, long now) {
        if (now - lastScan < SCAN_TICKS && now >= lastScan) {
            return;
        }
        lastScan = now;
        Set<Long> sectionsNow = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(REACH, REACH, REACH)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(REACH, REACH, REACH)));
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                ChunkAccess chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    scanChunk(chunk, low.y(), high.y(), sectionsNow, found);
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        keep(found.subList(0, Math.min(found.size(), MOST_DRAWN)), now);
        readSections.clear();
        readSections.addAll(sectionsNow);
        foundLastScan.clear();
        foundLastScan.addAll(found);
    }

    private void keep(List<BlockPos> nearest, long now) {
        Map<BlockPos, Cell> kept = new HashMap<>();
        for (BlockPos pos : nearest) {
            Cell standing = cells.get(pos);
            kept.put(pos, standing != null ? standing : new Cell(
                    landedAt(readSections.contains(SectionPos.asLong(pos)), foundLastScan.contains(pos), now),
                    ShardTable.fromSeed(seeds.getAsLong(), ShatterCellShards.SHARDS_PER_CELL)));
        }
        cells.clear();
        cells.putAll(kept);
    }

    private static void scanChunk(ChunkAccess chunk, int lowY, int highY, Set<Long> sectionsNow,
                                  List<BlockPos> found) {
        for (int sy = Math.max(lowY, chunk.getMinSectionY()); sy <= Math.min(highY, chunk.getMaxSectionY()); sy++) {
            sectionsNow.add(SectionPos.asLong(chunk.getPos().x(), sy, chunk.getPos().z()));
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            if (section.hasOnlyAir() || !section.getStates().maybeHas(ShatterCells::isShatter)) {
                continue;
            }
            BlockPos base = new BlockPos(chunk.getPos().getMinBlockX(), SectionPos.sectionToBlockCoord(sy),
                    chunk.getPos().getMinBlockZ());
            scanSection(section, base, found);
        }
    }

    private static void scanSection(LevelChunkSection section, BlockPos base, List<BlockPos> found) {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    if (isShatter(section.getBlockState(x, y, z))) {
                        found.add(base.offset(x, y, z));
                    }
                }
            }
        }
    }

    /**
     * Whether a block state is a Shatter cell.
     *
     * @param state the block state
     * @return true for a Shatter cell
     */
    static boolean isShatter(BlockState state) {
        return state.is(GooBlocks.CRYSTAL_SHATTER.get());
    }
}
