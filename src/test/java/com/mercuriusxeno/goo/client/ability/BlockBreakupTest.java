package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A crumbling block comes apart into chunks of its own texture: its cells
 * merge into a few rigid chunks of mixed size filling the block's outline,
 * which stand in place until their turn and crumble off in random order from
 * the outside of what still stands over the crumble; a whole block draws as
 * its shell (decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity).
 */
class BlockBreakupTest {

    private static final double ON_THE_LINE = 1e-6;
    private static final long START = 100;
    private static final long END = 140;
    private static final double TICK = 1;
    private static final double FRAME = 0.25;
    private static final long SEED = 7L;
    private static final int SEEDS = 64;
    private static final BlockPos POS = new BlockPos(7, 2, 3);
    private static final AABB WHOLE = new AABB(0, 0, 0, 1, 1, 1);
    private static final AABB SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    private static final int SHELL_FACES = 6 * BlockBreakup.CELLS_ACROSS * BlockBreakup.CELLS_ACROSS;
    /** The most chunks a block breaks into: a quarter of its cells. */
    private static final int FEWEST_CHUNKS_CAP = BlockBreakup.CELLS / 4;

    private static BlockBreakup.Breakup breakup(long seed) {
        return BlockBreakup.chunksOf(POS, START, END, WHOLE, RandomSource.create(seed));
    }

    private static int standingAt(List<BlockBreakup.Chunk> chunks, double now) {
        int standing = 0;
        for (boolean stands : BlockBreakup.standingOf(chunks, now)) {
            standing += stands ? 1 : 0;
        }
        return standing;
    }

    private static int facesShowing(BlockBreakup.Breakup breakup, double now) {
        boolean[] standing = BlockBreakup.standingOf(breakup.chunks(), now);
        int showing = 0;
        for (int cell = 0; cell < BlockBreakup.CELLS; cell++) {
            for (Direction face : Direction.values()) {
                if (BlockBreakup.shows(breakup, cell, face, standing)) {
                    showing++;
                }
            }
        }
        return showing;
    }

    @Nested
    class Crumble {

        @Test
        void theBlockStandsWholeUntilItsCrumbleStartsAndEveryChunkLeavesBeforeItEnds() {
            List<BlockBreakup.Chunk> chunks = breakup(SEED).chunks();
            assertEquals(chunks.size(), standingAt(chunks, START - TICK));
            double last = chunks.stream().mapToDouble(BlockBreakup.Chunk::release).max().orElseThrow();
            assertTrue(last < END, "the last chunk leaves at " + last + ", after the crumble ends");
            for (double now = START; now < last; now += FRAME) {
                assertTrue(standingAt(chunks, now) > 0, "no chunk stands at " + now);
            }
            assertEquals(0, standingAt(chunks, END));
        }

        @Test
        void eachChunkCrumblesOffTheOutsideOfWhatStillStands() {
            for (long seed = 0; seed < SEEDS; seed++) {
                BlockBreakup.Breakup breakup = breakup(seed);
                List<BlockBreakup.Chunk> chunks = breakup.chunks();
                boolean[] gone = new boolean[chunks.size()];
                for (int turn = 0; turn < chunks.size(); turn++) {
                    BlockBreakup.Chunk chunk = chunks.get(turn);
                    assertTrue(BlockBreakup.onOutside(new int[][] {chunk.least(), chunk.span()},
                            breakup.chunkOfCell(), gone), "chunk " + turn + " of seed " + seed + " left from inside");
                    gone[turn] = true;
                }
            }
        }

        @Test
        void blocksCrumbleInTheirOwnRandomOrder() {
            List<Vec3> first = breakup(SEED).chunks().stream().map(BlockBreakup.Chunk::place).toList();
            List<Vec3> second = breakup(SEED + 1).chunks().stream().map(BlockBreakup.Chunk::place).toList();
            assertNotEquals(first, second);
        }

        @Test
        void chunksLeaveInTurnOrderSpreadEvenlyOverTheCrumble() {
            List<BlockBreakup.Chunk> chunks = breakup(SEED).chunks();
            double crumble = END - START;
            for (int turn = 0; turn < chunks.size(); turn++) {
                assertEquals(START + crumble * turn / chunks.size(), chunks.get(turn).release(), ON_THE_LINE);
            }
        }
    }

    @Nested
    class Merge {

        @Test
        void neighbouringCellsMergeIntoAFewChunksOfMixedSize() {
            for (long seed = 0; seed < SEEDS; seed++) {
                List<BlockBreakup.Chunk> chunks = breakup(seed).chunks();
                assertTrue(chunks.size() <= FEWEST_CHUNKS_CAP, chunks.size() + " chunks from seed " + seed);
                assertTrue(chunks.stream().anyMatch(chunk -> chunk.cells() > 2), "no big chunk from seed " + seed);
                assertTrue(chunks.stream().anyMatch(chunk -> chunk.cells()
                        < BlockBreakup.CELLS / BlockBreakup.CELLS_ACROSS / 2), "no small chunk from seed " + seed);
                for (BlockBreakup.Chunk chunk : chunks) {
                    for (int span : chunk.span()) {
                        assertTrue(span >= 1 && span <= BlockBreakup.LONGEST_SPAN, "a chunk spans " + span);
                    }
                }
            }
        }

        @Test
        void everyCellBelongsToTheOneChunkWhoseBoxHoldsIt() {
            BlockBreakup.Breakup breakup = breakup(SEED);
            assertEquals(BlockBreakup.CELLS, breakup.chunks().stream().mapToInt(BlockBreakup.Chunk::cells).sum());
            for (int cell = 0; cell < BlockBreakup.CELLS; cell++) {
                BlockBreakup.Chunk chunk = breakup.chunks().get(breakup.chunkOfCell()[cell]);
                int[] place = {BlockBreakup.xOf(cell), BlockBreakup.yOf(cell), BlockBreakup.zOf(cell)};
                for (int axis = 0; axis < place.length; axis++) {
                    assertTrue(place[axis] >= chunk.least()[axis]
                            && place[axis] < chunk.least()[axis] + chunk.span()[axis], "cell " + cell + " outside");
                }
            }
        }

        @Test
        void chunksFillTheBlocksOwnOutline() {
            BlockBreakup.Breakup breakup = BlockBreakup.chunksOf(POS, START, END, SLAB, RandomSource.create(SEED));
            for (int cell = 0; cell < BlockBreakup.CELLS; cell++) {
                Vec3 local = breakup.placeOf(cell).subtract(POS.getX(), POS.getY(), POS.getZ());
                assertTrue(SLAB.contains(local), "a cell at " + local + " outside the slab");
            }
            Vec3 size = breakup.cellSize();
            assertEquals(0.5, BlockBreakup.CELLS * size.x * size.y * size.z, ON_THE_LINE);
        }
    }

    @Nested
    class Faces {

        @Test
        void aWholeBlockOfStandingChunksShowsOnlyItsShell() {
            assertEquals(SHELL_FACES, facesShowing(breakup(SEED), START - TICK));
        }

        @Test
        void aChunkThatLeftBaresTheFacesBehindIt() {
            BlockBreakup.Breakup breakup = breakup(SEED);
            BlockBreakup.Chunk first = breakup.chunks().getFirst();
            assertTrue(facesShowing(breakup, first.release() + FRAME) > SHELL_FACES,
                    "nothing bared behind the chunk that left");
        }

        @Test
        void eachCellFaceOfABlockFaceWearsItsOwnCellOfTheTexture() {
            BlockBreakup.Breakup breakup = breakup(SEED);
            boolean[] standing = BlockBreakup.standingOf(breakup.chunks(), START - TICK);
            for (Direction face : Direction.values()) {
                long cells = IntStream.range(0, BlockBreakup.CELLS)
                        .filter(cell -> BlockBreakup.shows(breakup, cell, face, standing))
                        .mapToObj(cell -> BlockBreakup.faceCellOf(cell, face)).distinct().count();
                assertEquals((long) BlockBreakup.CELLS_ACROSS * BlockBreakup.CELLS_ACROSS, cells, face + " face");
            }
        }

        @Test
        void theTopFacesCellsRunAlongXAcrossAndZDown() {
            int eastNorthTop = BlockBreakup.cellAt(BlockBreakup.CELLS_ACROSS - 1, BlockBreakup.CELLS_ACROSS - 1, 0);
            assertEquals(new BlockBreakup.FaceCell(BlockBreakup.CELLS_ACROSS - 1, 0),
                    BlockBreakup.faceCellOf(eastNorthTop, Direction.UP));
            assertEquals(new BlockBreakup.FaceCell(0, 0), BlockBreakup.faceCellOf(eastNorthTop, Direction.NORTH));
        }
    }
}
