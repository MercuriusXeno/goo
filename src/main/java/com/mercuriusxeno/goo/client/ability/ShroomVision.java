package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Sculker's shroom vision: while Sculker stands, every fungus block within reach, a block whose block item's goo value
 * holds sculk (FungusBlocks), is in what the sense view draws at any noise level, with no echo needed to reveal it,
 * beside the blocks the echoes lit. The blocks are scanned again once a second, chunk section by chunk section,
 * skipping any section whose palette holds none and reading each section's blocks once, until a block in it
 * changes or its chunk loads or unloads, and the nearest are kept up to a cap.
 * decision sculker-absorbs-sight-as-shroom-vision
 */
public final class ShroomVision {

    /** How far the vision reaches, in blocks, as Transmit's reach did under Sight. */
    static final double VISION_REACH = 64;
    static final int RESCAN_TICKS = 20;
    private static final int MOST_SEEN = 512;
    private static final int SECTION_SIZE = SectionPos.SECTION_SIZE;
    private static final int BLOCKS_A_SECTION = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    /** Marks that no scan has run. */
    static final long UNSCANNED = Long.MIN_VALUE;

    private static final List<BlockPos> SEEN = new ArrayList<>();
    /**
     * Each chunk section's fungus blocks within the vision's reach, in the
     * order a scan meets them, by packed section position: a section's
     * fungus changes only when a block in it changes or its chunk loads or
     * unloads, so a scan reads each section's blocks once and those changes
     * alone drop it. A section not loaded or holding none keeps none.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     */
    private static final Long2ObjectOpenHashMap<List<BlockPos>> SECTIONS = new Long2ObjectOpenHashMap<>();
    /** Each block a scan found within reach, its squared distance from the eyes, beside it in SEEN. */
    private static final DoubleArrayList DISTANCES = new DoubleArrayList();
    /** From a block's corner to its center, along each axis. */
    private static final double HALF = 0.5;
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
     * What the vision reads of the world: each chunk section's blocks where
     * it may hold a fungus block, and the block standing at a spot, so a
     * test counts and times a scan headless.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     */
    interface ShroomWorld {

        /**
         * @param sectionX the section's x
         * @param sectionY the section's y
         * @param sectionZ the section's z
         * @return the section's blocks, or null where it is not loaded, holds only air or its palette holds no
         *         fungus block
         */
        @Nullable FungusSection fungusSection(int sectionX, int sectionY, int sectionZ);

        /**
         * @param pos the block
         * @return the block standing there
         */
        BlockState stateAt(BlockPos pos);
    }

    /** One chunk section's blocks, asked one by one. */
    @FunctionalInterface
    interface FungusSection {

        /**
         * @param x the block's x within the section, 0 to 15
         * @param y the block's y within the section, 0 to 15
         * @param z the block's z within the section, 0 to 15
         * @return true where a fungus block stands
         */
        boolean holdsFungusAt(int x, int y, int z);
    }

    /**
     * The loaded level, read section by section through each chunk's palette.
     *
     * @param level  the level
     * @param fungus the fungus blocks
     */
    record LevelShroomWorld(Level level, Set<Block> fungus) implements ShroomWorld {

        @Override
        public @Nullable FungusSection fungusSection(int sectionX, int sectionY, int sectionZ) {
            ChunkAccess chunk = level.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
            if (chunk == null || sectionY < chunk.getMinSectionY() || sectionY > chunk.getMaxSectionY()) {
                return null;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> fungus.contains(state.getBlock()))) {
                return null;
            }
            return (x, y, z) -> fungus.contains(section.getBlockState(x, y, z).getBlock());
        }

        @Override
        public BlockState stateAt(BlockPos pos) {
            return level.getBlockState(pos);
        }
    }

    /**
     * The shroom network blocks within the vision's reach of the local player and what stands there now, the
     * scan run again once a second.
     *
     * @param world the world the vision reads
     * @param eye   the local player's eyes
     * @param now   the game time
     * @return each block nearby and its state
     */
    static Map<BlockPos, BlockState> nearby(ShroomWorld world, Vec3 eye, long now) {
        return SculkerEchoSections.profiled(SculkerEchoSections.SHROOM_NEARBY, () -> {
            if (isDue(scannedAt, now)) {
                SculkerEchoSections.profiled(SculkerEchoSections.SHROOM_RESCAN, () -> rescan(world, eye));
                scannedAt = now;
            }
            Map<BlockPos, BlockState> nearby = new HashMap<>();
            for (BlockPos pos : SEEN) {
                nearby.put(pos, world.stateAt(pos));
            }
            SculkerEchoSections.count(SculkerEchoSections.SHROOM_STATES_COUNTER, SEEN.size());
            return nearby;
        });
    }

    /**
     * Drops the kept fungus blocks of the section a changed block stands in,
     * so the next scan reads that section again.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     *
     * @param pos the block that changed
     */
    static void blockChanged(BlockPos pos) {
        SECTIONS.remove(SectionPos.asLong(pos));
    }

    /**
     * Drops the kept fungus blocks of every section of a chunk that loaded or
     * unloaded, so the next scan reads its column again.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     *
     * @param chunkX the chunk's x
     * @param chunkZ the chunk's z
     */
    static void chunkChanged(int chunkX, int chunkZ) {
        SECTIONS.keySet().removeIf((long section) -> SectionPos.x(section) == chunkX && SectionPos.z(section) == chunkZ);
    }

    /** Forgets the last scan and every section's kept fungus blocks, so the next vision scans afresh. */
    static void forget() {
        SEEN.clear();
        SECTIONS.clear();
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

    private static void rescan(ShroomWorld world, Vec3 eye) {
        SEEN.clear();
        DISTANCES.clear();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(VISION_REACH, VISION_REACH, VISION_REACH)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(VISION_REACH, VISION_REACH, VISION_REACH)));
        int read = 0;
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                for (int sy = low.y(); sy <= high.y(); sy++) {
                    read += addSectionWithinReach(world, SectionPos.of(sx, sy, sz), eye);
                }
            }
        }
        SECTIONS.keySet().removeIf((long section) -> !within(section, low, high));
        SculkerEchoSections.count(SculkerEchoSections.SHROOM_SECTIONS_COUNTER, read);
        SculkerEchoSections.count(SculkerEchoSections.SHROOM_BLOCKS_COUNTER, read * BLOCKS_A_SECTION);
        keepTheNearest();
    }

    /**
     * Keeps the nearest fungus blocks found, up to the cap, nearest first,
     * blocks at one distance in the order the scan met them: each block's
     * distance worked out once, beside it, rather than at every comparison.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     */
    private static void keepTheNearest() {
        int found = SEEN.size();
        int[] order = new int[found];
        for (int index = 0; index < found; index++) {
            order[index] = index;
        }
        IntArrays.quickSort(order, (first, second) -> {
            int byDistance = Double.compare(DISTANCES.getDouble(first), DISTANCES.getDouble(second));
            return byDistance != 0 ? byDistance : Integer.compare(first, second);
        });
        List<BlockPos> nearest = new ArrayList<>(Math.min(found, MOST_SEEN));
        for (int index = 0; index < Math.min(found, MOST_SEEN); index++) {
            nearest.add(SEEN.get(order[index]));
        }
        SEEN.clear();
        SEEN.addAll(nearest);
        DISTANCES.clear();
    }

    /**
     * Adds a section's fungus blocks within reach, its blocks read once and kept until a change drops them.
     *
     * @param world   the world the vision reads
     * @param section the section
     * @param eye     the local player's eyes
     * @return 1 where the section's blocks were read now, 0 where they were kept or it holds none
     */
    private static int addSectionWithinReach(ShroomWorld world, SectionPos section, Vec3 eye) {
        long key = section.asLong();
        List<BlockPos> fungus = SECTIONS.get(key);
        int read = 0;
        if (fungus == null) {
            FungusSection blocks = world.fungusSection(section.x(), section.y(), section.z());
            fungus = blocks == null ? List.of() : fungusIn(blocks, section.origin());
            read = blocks == null ? 0 : 1;
            SECTIONS.put(key, fungus);
        }
        addWithinReach(fungus, eye);
        return read;
    }

    private static boolean within(long section, SectionPos low, SectionPos high) {
        return between(SectionPos.x(section), low.x(), high.x()) && between(SectionPos.y(section), low.y(), high.y())
                && between(SectionPos.z(section), low.z(), high.z());
    }

    private static boolean between(int coordinate, int low, int high) {
        return coordinate >= low && coordinate <= high;
    }

    private static void addWithinReach(List<BlockPos> fungus, Vec3 eye) {
        for (BlockPos pos : fungus) {
            double dx = pos.getX() + HALF - eye.x;
            double dy = pos.getY() + HALF - eye.y;
            double dz = pos.getZ() + HALF - eye.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (Math.sqrt(distanceSquared) <= VISION_REACH) {
                SEEN.add(pos);
                DISTANCES.add(distanceSquared);
            }
        }
    }

    /**
     * Every fungus block of a section, in the order a scan meets them.
     *
     * @param section the section's blocks
     * @param base    the section's lowest corner
     * @return the fungus blocks
     */
    private static List<BlockPos> fungusIn(FungusSection section, BlockPos base) {
        List<BlockPos> fungus = new ArrayList<>();
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    if (section.holdsFungusAt(x, y, z)) {
                        fungus.add(base.offset(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(fungus);
    }
}
