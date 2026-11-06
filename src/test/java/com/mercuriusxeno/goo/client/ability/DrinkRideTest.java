package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drained block peels into chunks of its own texture: the chunks fill the
 * block's outline, stand in place until their turn, leave corner by corner
 * from the corner nearest the hand over the drain, the block standing until
 * its last chunk leaves, and each flies along the line from its place to the
 * glove at the stream's base pace, done at the glove
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
    private static final BlockPos POS = new BlockPos(7, 2, 3);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(POS, Vec3.atCenterOf(POS), 1, PICKED, LANDS,
            DRAINED);
    private static final AABB WHOLE = new AABB(0, 0, 0, 1, 1, 1);
    private static final AABB SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    /** A glove beside the block, as near as the hand is while it drinks. */
    private static final Vec3 GLOVE = new Vec3(4.5, 3.2, 1.1);
    /** A glove so far off that no chunk reaches it within the drain, so every chunk that left is still flying. */
    private static final Vec3 FAR_GLOVE = Vec3.atCenterOf(POS).add(-40, 0, 0);

    private static List<DrinkRide.Chunk> chunks() {
        return DrinkRide.chunksOf(BLOCK, WHOLE, GLOVE);
    }

    @Nested
    class Peel {

        @Test
        void atHalfTheDrainAboutHalfTheChunksStandAndTheRestFlyToTheHand() {
            List<DrinkRide.Chunk> chunks = DrinkRide.chunksOf(BLOCK, WHOLE, FAR_GLOVE);
            int standing = DrinkRide.standingAt(chunks, HALF_TIME);
            assertTrue(Math.abs(standing - chunks.size() / 2) <= chunks.size() / 8,
                    standing + " of " + chunks.size() + " chunks stand at half the drain");
            int flying = 0;
            for (DrinkRide.Chunk chunk : chunks) {
                Vec3 at = DrinkRide.pointOf(chunk, FAR_GLOVE, HALF_TIME);
                assertNotNull(at, "a chunk reached a glove no chunk can reach yet");
                if (HALF_TIME <= chunk.release()) {
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
            List<DrinkRide.Chunk> chunks = chunks();
            assertEquals(chunks.size(), DrinkRide.standingAt(chunks, LANDS - TICK));
            double last = chunks.stream().mapToDouble(DrinkRide.Chunk::release).max().orElseThrow();
            assertTrue(last < DRAINED, "the last chunk leaves at " + last + ", after the drain ends");
            for (double now = LANDS; now < last; now += FRAME) {
                assertTrue(DrinkRide.standingAt(chunks, now) > 0, "no chunk stands at " + now);
            }
            assertEquals(0, DrinkRide.standingAt(chunks, DRAINED));
        }

        @Test
        void chunksLeaveCornerByCornerFromTheCornerNearestTheHand() {
            List<DrinkRide.Chunk> chunks = chunks();
            Vec3 corner = new Vec3(POS.getX(), POS.getY() + 1, POS.getZ());
            List<DrinkRide.Chunk> byTurn = chunks.stream().sorted(Comparator.comparingDouble(DrinkRide.Chunk::release))
                    .toList();
            for (int index = 1; index < byTurn.size(); index++) {
                double before = byTurn.get(index - 1).place().distanceTo(corner);
                double after = byTurn.get(index).place().distanceTo(corner);
                assertTrue(before <= after + ON_THE_LINE, "a chunk " + after + " from the hand's corner left before"
                        + " one " + before + " from it");
            }
        }

        @Test
        void chunksFillTheBlocksOwnOutline() {
            List<DrinkRide.Chunk> chunks = DrinkRide.chunksOf(BLOCK, SLAB, GLOVE);
            double filled = 0;
            for (DrinkRide.Chunk chunk : chunks) {
                Vec3 local = chunk.place().subtract(POS.getX(), POS.getY(), POS.getZ());
                assertTrue(SLAB.contains(local), "a chunk at " + local + " outside the slab");
                filled += chunk.width() * chunk.width() * chunk.height();
            }
            assertEquals(0.5, filled, ON_THE_LINE);
        }

        @Test
        void eachChunkWearsItsOwnCellOfTheSprite() {
            List<DrinkRide.Chunk> chunks = chunks();
            long cells = chunks.stream().map(chunk -> chunk.across() * DrinkRide.CHUNKS_ACROSS + chunk.down())
                    .distinct().count();
            assertEquals((long) DrinkRide.CHUNKS_ACROSS * DrinkRide.CHUNKS_ACROSS, cells);
        }
    }

    @Nested
    class Flight {

        private final DrinkRide.Chunk chunk = new DrinkRide.Chunk(new Vec3(7.2, 2.7, 3.4), 0.25, 0.25, 0, 0,
                LANDS + TICK);

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
    }
}
