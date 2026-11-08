package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drained block peels into chunks of its own texture: its cells merge
 * into a few rigid chunks of mixed size filling the block's outline, which
 * stand in place until their turn, crumble off in random order from the
 * outside of what still stands over the drain, the block standing until its
 * last chunk leaves, and each flies along the line from its place to the glove at the
 * stream's base pace, done at the glove; a whole block draws as its shell
 * (decisions unmake-peels-the-block-into-chunks-that-stream-to-the-hand,
 * unmake-drink-is-a-stream-of-break-particles).
 */
class DrinkRideTest {

    private static final double ON_THE_LINE = 1e-6;
    private static final long LANDS = 100;
    private static final long DRAINED = 140;
    private static final double HALF_TIME = (LANDS + DRAINED) / 2.0;
    private static final long PICKED = LANDS - SiphonRule.INJECT_TICKS;
    private static final double TICK = 1;
    private static final double FRAME = 0.25;
    private static final long SEED = 7L;
    private static final int SEEDS = 64;
    private static final BlockPos POS = new BlockPos(7, 2, 3);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(POS, Vec3.atCenterOf(POS), 1, PICKED, LANDS,
            DRAINED);
    private static final AABB WHOLE = new AABB(0, 0, 0, 1, 1, 1);
    private static final AABB SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    /** A glove beside the block, as near as the hand is while it drinks. */
    private static final Vec3 GLOVE = new Vec3(4.5, 3.2, 1.1);
    /** A glove so far off that no chunk reaches it within the drain, so every chunk that left is still flying. */
    private static final Vec3 FAR_GLOVE = Vec3.atCenterOf(POS).add(-40, 0, 0);
    private static final int SHELL_FACES = 6 * DrinkRide.CELLS_ACROSS * DrinkRide.CELLS_ACROSS;
    /** The most chunks a block breaks into: a quarter of its cells. */
    private static final int FEWEST_CHUNKS_CAP = DrinkRide.CELLS / 4;

    private static DrinkRide.Breakup breakup(long seed) {
        return DrinkRide.chunksOf(BLOCK, WHOLE, RandomSource.create(seed));
    }

    private static int facesShowing(DrinkRide.Breakup breakup, double now) {
        boolean[] standing = DrinkRide.standingOf(breakup.chunks(), now);
        int showing = 0;
        for (int cell = 0; cell < DrinkRide.CELLS; cell++) {
            for (Direction face : Direction.values()) {
                if (DrinkRide.shows(breakup, cell, face, standing)) {
                    showing++;
                }
            }
        }
        return showing;
    }

    @Nested
    class Peel {

        @Test
        void atHalfTheDrainAboutHalfTheChunksStandAndTheRestFlyToTheHand() {
            List<DrinkRide.Chunk> chunks = DrinkRide.chunksOf(BLOCK, WHOLE, RandomSource.create(SEED))
                    .chunks();
            int standing = DrinkRide.standingAt(chunks, HALF_TIME);
            assertTrue(Math.abs(standing - chunks.size() / 2.0) <= 1,
                    standing + " of " + chunks.size() + " chunks stand at half the drain");
            int flying = 0;
            for (DrinkRide.Chunk chunk : chunks) {
                Vec3 at = DrinkRide.pointOf(chunk, FAR_GLOVE, HALF_TIME);
                assertNotNull(at, "a chunk reached a glove no chunk can reach yet");
                if (DrinkRide.stands(chunk, HALF_TIME)) {
                    assertEquals(chunk.place(), at, "a standing chunk off its place");
                } else {
                    assertNotEquals(chunk.place(), at, "a chunk that left still at its place");
                    assertEquals(chunk.place().distanceTo(FAR_GLOVE), chunk.place().distanceTo(at)
                            + at.distanceTo(FAR_GLOVE), ON_THE_LINE, "a flying chunk off its line to the hand");
                    flying++;
                }
            }
            assertEquals(chunks.size() - standing, flying);
        }

        @Test
        void theBlockStandsWholeUntilItsSquareLandsAndNeverReadsAbsentBeforeItsLastChunkLeaves() {
            List<DrinkRide.Chunk> chunks = breakup(SEED).chunks();
            assertEquals(chunks.size(), DrinkRide.standingAt(chunks, LANDS - TICK));
            double last = chunks.stream().mapToDouble(DrinkRide.Chunk::release).max().orElseThrow();
            assertTrue(last < DRAINED, "the last chunk leaves at " + last + ", after the drain ends");
            for (double now = LANDS; now < last; now += FRAME) {
                assertTrue(DrinkRide.standingAt(chunks, now) > 0, "no chunk stands at " + now);
            }
            assertEquals(0, DrinkRide.standingAt(chunks, DRAINED));
        }

        @Test
        void eachChunkCrumblesOffTheOutsideOfWhatStillStands() {
            for (long seed = 0; seed < SEEDS; seed++) {
                DrinkRide.Breakup breakup = breakup(seed);
                List<DrinkRide.Chunk> chunks = breakup.chunks();
                boolean[] gone = new boolean[chunks.size()];
                for (int turn = 0; turn < chunks.size(); turn++) {
                    DrinkRide.Chunk chunk = chunks.get(turn);
                    assertTrue(DrinkRide.onOutside(new int[][] {chunk.least(), chunk.span()}, breakup.chunkOfCell(),
                            gone), "chunk " + turn + " of seed " + seed + " left from inside the block");
                    gone[turn] = true;
                }
            }
        }

        @Test
        void blocksCrumbleInTheirOwnRandomOrder() {
            List<Vec3> first = breakup(SEED).chunks().stream().map(DrinkRide.Chunk::place).toList();
            List<Vec3> second = breakup(SEED + 1).chunks().stream().map(DrinkRide.Chunk::place).toList();
            assertNotEquals(first, second);
        }

        @Test
        void chunksLeaveInTurnOrderSpreadEvenlyOverTheDrain() {
            List<DrinkRide.Chunk> chunks = breakup(SEED).chunks();
            double drain = DRAINED - LANDS;
            for (int turn = 0; turn < chunks.size(); turn++) {
                assertEquals(LANDS + drain * turn / chunks.size(), chunks.get(turn).release(), ON_THE_LINE);
            }
        }
    }

    @Nested
    class Merge {

        @Test
        void neighbouringCellsMergeIntoAFewChunksOfMixedSize() {
            for (long seed = 0; seed < SEEDS; seed++) {
                List<DrinkRide.Chunk> chunks = breakup(seed).chunks();
                // unmake-converges-near-the-unmade-blocks-into-a-goo-item: few enough that a blob's chunks never crowd
                assertTrue(chunks.size() <= FEWEST_CHUNKS_CAP, chunks.size() + " chunks from seed " + seed);
                assertTrue(chunks.stream().anyMatch(chunk -> chunk.cells() > 2), "no big chunk from seed " + seed);
                assertTrue(chunks.stream().anyMatch(chunk -> chunk.cells() < DrinkRide.CELLS / DrinkRide.CELLS_ACROSS
                        / 2), "no small chunk from seed " + seed);
                for (DrinkRide.Chunk chunk : chunks) {
                    for (int span : chunk.span()) {
                        assertTrue(span >= 1 && span <= DrinkRide.LONGEST_SPAN, "a chunk spans " + span);
                    }
                }
            }
        }

        @Test
        void everyCellBelongsToTheOneChunkWhoseBoxHoldsIt() {
            DrinkRide.Breakup breakup = breakup(SEED);
            assertEquals(DrinkRide.CELLS, breakup.chunks().stream().mapToInt(DrinkRide.Chunk::cells).sum());
            for (int cell = 0; cell < DrinkRide.CELLS; cell++) {
                DrinkRide.Chunk chunk = breakup.chunks().get(breakup.chunkOfCell()[cell]);
                int[] place = {DrinkRide.xOf(cell), DrinkRide.yOf(cell), DrinkRide.zOf(cell)};
                for (int axis = 0; axis < place.length; axis++) {
                    assertTrue(place[axis] >= chunk.least()[axis]
                            && place[axis] < chunk.least()[axis] + chunk.span()[axis], "cell " + cell + " outside");
                }
            }
        }

        @Test
        void chunksFillTheBlocksOwnOutline() {
            DrinkRide.Breakup breakup = DrinkRide.chunksOf(BLOCK, SLAB, RandomSource.create(SEED));
            for (int cell = 0; cell < DrinkRide.CELLS; cell++) {
                Vec3 local = breakup.placeOf(cell).subtract(POS.getX(), POS.getY(), POS.getZ());
                assertTrue(SLAB.contains(local), "a cell at " + local + " outside the slab");
            }
            Vec3 size = breakup.cellSize();
            assertEquals(0.5, DrinkRide.CELLS * size.x * size.y * size.z, ON_THE_LINE);
        }
    }

    @Nested
    class Faces {

        @Test
        void aWholeBlockOfStandingChunksShowsOnlyItsShell() {
            assertEquals(SHELL_FACES, facesShowing(breakup(SEED), LANDS - TICK));
        }

        @Test
        void aChunkThatLeftShowsItsWholeSurfaceAndBaresTheFacesBehindIt() {
            DrinkRide.Breakup breakup = breakup(SEED);
            DrinkRide.Chunk first = breakup.chunks().getFirst();
            double gone = first.release() + FRAME;
            boolean[] standing = DrinkRide.standingOf(breakup.chunks(), gone);
            int[] span = first.span();
            int surface = 2 * (span[1] * span[2] + span[0] * span[2] + span[0] * span[1]);
            int ownShowing = 0;
            for (int cell = 0; cell < DrinkRide.CELLS; cell++) {
                for (Direction face : Direction.values()) {
                    if (breakup.chunkOfCell()[cell] == 0 && DrinkRide.shows(breakup, cell, face, standing)) {
                        ownShowing++;
                    }
                }
            }
            assertEquals(surface, ownShowing, "the flying chunk's own faces showing");
            assertTrue(facesShowing(breakup, gone) > SHELL_FACES, "nothing bared behind the chunk that left");
        }

        @Test
        void eachCellFaceOfABlockFaceWearsItsOwnCellOfTheTexture() {
            DrinkRide.Breakup breakup = breakup(SEED);
            boolean[] standing = DrinkRide.standingOf(breakup.chunks(), LANDS - TICK);
            for (Direction face : Direction.values()) {
                long cells = java.util.stream.IntStream.range(0, DrinkRide.CELLS)
                        .filter(cell -> DrinkRide.shows(breakup, cell, face, standing))
                        .mapToObj(cell -> DrinkRide.faceCellOf(cell, face)).distinct().count();
                assertEquals((long) DrinkRide.CELLS_ACROSS * DrinkRide.CELLS_ACROSS, cells, face + " face");
            }
        }

        @Test
        void theTopFacesCellsRunAlongXAcrossAndZDown() {
            int eastNorthTop = DrinkRide.cellAt(DrinkRide.CELLS_ACROSS - 1, DrinkRide.CELLS_ACROSS - 1, 0);
            assertEquals(new DrinkRide.FaceCell(DrinkRide.CELLS_ACROSS - 1, 0),
                    DrinkRide.faceCellOf(eastNorthTop, Direction.UP));
            assertEquals(new DrinkRide.FaceCell(0, 0), DrinkRide.faceCellOf(eastNorthTop, Direction.NORTH));
        }
    }

    @Nested
    class Flight {

        private final DrinkRide.Chunk chunk = new DrinkRide.Chunk(new Vec3(7.2, 2.7, 3.4), new int[] {0, 0, 0},
                new int[] {1, 1, 1}, LANDS + TICK);

        @Test
        void aChunkLiesOnTheLineFromItsPlaceToTheGloveAtThePacesDistance() {
            double span = chunk.place().distanceTo(GLOVE);
            int steps = 0;
            for (double now = chunk.release(); now < chunk.release() + span / DrinkStream.FLOW; now += TICK) {
                Vec3 at = DrinkRide.pointOf(chunk, GLOVE, now);
                assertNotNull(at, "a chunk short of the glove at " + now);
                double flown = DrinkStream.FLOW * (now - chunk.release());
                assertEquals(flown, chunk.place().distanceTo(at), ON_THE_LINE);
                assertEquals(span - flown, at.distanceTo(GLOVE), ON_THE_LINE);
                steps++;
            }
            assertTrue(steps > 1, "the flight took " + steps + " steps");
        }

        @Test
        void aChunkFollowsTheGloveAsItMoves() {
            Vec3 moved = GLOVE.add(0, 1, 0);
            Vec3 at = DrinkRide.pointOf(chunk, moved, chunk.release() + TICK);
            assertNotNull(at);
            double span = chunk.place().distanceTo(moved);
            assertEquals(span, chunk.place().distanceTo(at) + at.distanceTo(moved), ON_THE_LINE);
        }

        @Test
        void aChunkIsDoneOnceItsDistanceReachesTheGlove() {
            double span = chunk.place().distanceTo(GLOVE);
            assertFalse(DrinkRide.done(chunk.place(), GLOVE, span - ON_THE_LINE * 2));
            assertTrue(DrinkRide.done(chunk.place(), GLOVE, span));
            assertNull(DrinkRide.pointOf(chunk, GLOVE, chunk.release() + span / DrinkStream.FLOW + TICK));
        }

        /** The stream of chunks tapers into its blob (decision unmake-converges-near-the-unmade-blocks-into-a-goo-item). */
        @Test
        void aChunkShrinksAsItNearsItsBlob() {
            double span = chunk.place().distanceTo(GLOVE);
            double halfway = chunk.release() + span / DrinkStream.FLOW / 2;
            double nearly = chunk.release() + span / DrinkStream.FLOW * 0.9;

            assertEquals(1, DrinkRide.shrinkOf(chunk, GLOVE, chunk.release()), ON_THE_LINE);
            assertEquals(0.5, DrinkRide.shrinkOf(chunk, GLOVE, halfway), ON_THE_LINE);
            assertEquals(0, DrinkRide.shrinkOf(chunk, GLOVE, nearly), ON_THE_LINE,
                    "a chunk nearly in its blob is gone rather than crowding it");
        }
    }
}
