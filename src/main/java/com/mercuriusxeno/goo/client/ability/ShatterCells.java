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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * The Shatter cells this client draws shards over, held in client memory
 * only, since the cell is a plain block with no block entity and no chunk
 * storage: the chunk sections near the camera are scanned again every few
 * ticks, skipping any whose palette holds no Shatter cell, and the nearest
 * cells are kept. A cell's shards stay hidden in its goo until a living thing
 * comes within 4 blocks of it, then rise and fade in over the fade's ticks,
 * and sink back and fade out over as many once nothing living is that near,
 * so only cells something stands by draw shards and cast reflection rays.
 * Each cell's shard layout is drawn from a seed the client picks the first
 * time it sees the cell.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 * cloud-seed-per-client-ephemeral
 */
final class ShatterCells {

    /** How near a living thing stands to a cell's center to raise its shards, in blocks. */
    static final double NEAR_RADIUS = 4.0;
    /** Ticks a cell's shards take to rise and fade in, and to sink and fade out. */
    static final int FADE_TICKS = 20;
    /** Ticks between scans. */
    static final int SCAN_TICKS = 4;
    /** How far from the camera cells are scanned, in blocks. */
    static final double REACH = 24.0;
    /** The most cells drawn, the nearest kept, since each shard face casts its own reflection ray. */
    static final int MOST_DRAWN = 64;
    private static final int SECTION_SIZE = 16;
    /** The last scan's tick before any scan has run. */
    static final long NEVER_SCANNED = Long.MIN_VALUE;

    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final LongSupplier seeds;
    private long lastScan = NEVER_SCANNED;

    /**
     * One cell's shards: its layout, and how far they have risen out of its
     * goo this tick and the tick before, which a frame between reads between.
     */
    static final class Cell {

        private final ShatterCellShards.CellLayout layout;
        private float shown;
        private float shownBefore;

        /**
         * A cell whose shards stand hidden in its goo.
         *
         * @param layout the cell's shard layout
         */
        Cell(ShatterCellShards.CellLayout layout) {
            this.layout = layout;
        }

        /**
         * @return the cell's shard layout
         */
        ShatterCellShards.CellLayout layout() {
            return layout;
        }

        /**
         * How far the cell's shards stand risen out of its goo at a frame.
         *
         * @param partialTick the frame's share of the way from the last tick to the next
         * @return the shown share, 0 hidden to 1 at rest
         */
        float shownAt(float partialTick) {
            return shownBefore + (shown - shownBefore) * partialTick;
        }

        private void tick(boolean near) {
            shownBefore = shown;
            shown = shownAfterTick(shown, near);
        }
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
     * Whether a scan is due: the first, one a scan interval after the last,
     * or one after the clock ran backward. The first is asked by its sentinel
     * rather than by a difference, since the tick minus the sentinel overflows.
     *
     * @param lastScan the last scan's tick, or {@link #NEVER_SCANNED}
     * @param now      the game tick
     * @return true where the index scans this tick
     */
    static boolean isDue(long lastScan, long now) {
        return lastScan == NEVER_SCANNED || now < lastScan || now - lastScan >= SCAN_TICKS;
    }

    /**
     * Whether a living thing stands near enough a cell to raise its shards.
     *
     * @param cell   the cell
     * @param living where each living thing near the camera stands
     * @return true where one stands within {@link #NEAR_RADIUS} of the cell's center
     */
    static boolean isNear(BlockPos cell, List<Vec3> living) {
        Vec3 center = Vec3.atCenterOf(cell);
        return living.stream().anyMatch(at -> at.distanceToSqr(center) <= NEAR_RADIUS * NEAR_RADIUS);
    }

    /**
     * A cell's shown share a tick on: a fade's share higher while something
     * living is near, as much lower while nothing is, held between none and all.
     *
     * @param shown the share this tick
     * @param near  whether a living thing stands near the cell
     * @return the share next tick
     */
    static float shownAfterTick(float shown, boolean near) {
        float step = 1f / FADE_TICKS;
        return Math.clamp(shown + (near ? step : -step), 0f, 1f);
    }

    /**
     * Moves every cell's shards a tick toward risen where a living thing is near it, toward hidden where not.
     *
     * @param living where each living thing near the camera stands
     */
    void tickShown(List<Vec3> living) {
        cells.forEach((pos, cell) -> cell.tick(isNear(pos, living)));
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
        lastScan = NEVER_SCANNED;
    }

    /**
     * Scans the sections near the camera once its scan is due.
     *
     * @param level the client level
     * @param eye   the camera's position
     * @param now   the game tick
     */
    void rescanIfDue(Level level, Vec3 eye, long now) {
        if (!isDue(lastScan, now)) {
            return;
        }
        lastScan = now;
        List<BlockPos> found = new ArrayList<>();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(REACH, REACH, REACH)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(REACH, REACH, REACH)));
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                ChunkAccess chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    scanChunk(chunk, low.y(), high.y(), found);
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        keep(found.subList(0, Math.min(found.size(), MOST_DRAWN)));
    }

    private void keep(List<BlockPos> nearest) {
        Map<BlockPos, Cell> kept = new HashMap<>();
        for (BlockPos pos : nearest) {
            Cell standing = cells.get(pos);
            kept.put(pos, standing != null ? standing
                    : new Cell(ShatterCellShards.layoutFromSeed(seeds.getAsLong())));
        }
        cells.clear();
        cells.putAll(kept);
    }

    private static void scanChunk(ChunkAccess chunk, int lowY, int highY, List<BlockPos> found) {
        for (int sy = Math.max(lowY, chunk.getMinSectionY()); sy <= Math.min(highY, chunk.getMaxSectionY()); sy++) {
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
