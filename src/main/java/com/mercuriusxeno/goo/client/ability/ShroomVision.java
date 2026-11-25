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
    private static final int BLOCKS_A_SECTION = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
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

    private static void rescan(ShroomWorld world, Vec3 eye) {
        SEEN.clear();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(VISION_REACH, VISION_REACH, VISION_REACH)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(VISION_REACH, VISION_REACH, VISION_REACH)));
        int read = 0;
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                for (int sy = low.y(); sy <= high.y(); sy++) {
                    FungusSection section = world.fungusSection(sx, sy, sz);
                    if (section != null) {
                        scanSection(section, SectionPos.of(sx, sy, sz).origin(), eye);
                        read++;
                    }
                }
            }
        }
        SculkerEchoSections.count(SculkerEchoSections.SHROOM_SECTIONS_COUNTER, read);
        SculkerEchoSections.count(SculkerEchoSections.SHROOM_BLOCKS_COUNTER, read * BLOCKS_A_SECTION);
        SEEN.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        if (SEEN.size() > MOST_SEEN) {
            SEEN.subList(MOST_SEEN, SEEN.size()).clear();
        }
    }

    private static void scanSection(FungusSection section, BlockPos base, Vec3 eye) {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    if (section.holdsFungusAt(x, y, z)) {
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
