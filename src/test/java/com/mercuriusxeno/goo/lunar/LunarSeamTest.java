package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's seam: a coordinate past one edge wraps to the same distance in from the other, a grid cell past it rolls as
 * the cell in from the other edge, and a generated feature or structure whose footprint reaches across it is refused.
 * decision moon-wraps-at-its-border-like-the-zone
 */
class LunarSeamTest {

    private static final int SEAM = 7_500_000;
    private static final int CHUNK_ENDING_AT_THE_SEAM = SEAM / 16 - 1;

    @Nested
    class Wrap {

        @Test
        void anXPastTheEastEdgeWrapsToTheSameDistancePastTheWestEdge() {
            assertEquals(-7_499_990.0, LunarSeam.wrap(7_500_010.0));
        }

        @Test
        void aZPastTheWestEdgeWrapsToTheSameDistancePastTheEastEdge() {
            assertEquals(7_499_990.0, LunarSeam.wrap(-7_500_010.0));
        }

        @Test
        void aBlockPastTheEastEdgeWrapsToTheBlockPastTheWestEdge() {
            assertEquals(-7_499_990, LunarSeam.wrap(7_500_010));
        }

        @Test
        void theSeamItselfReadsAsTheWestEdge() {
            assertEquals(-SEAM, LunarSeam.wrap(SEAM));
        }

        @Test
        void aBlockInsideTheMoonStaysWhereItIs() {
            assertEquals(7_499_999, LunarSeam.wrap(7_499_999));
        }

        @Test
        void aCellPastTheEastEdgeRollsAsTheCellInFromTheWestEdge() {
            assertEquals(-SEAM / GiantCraterField.CELL,
                    LunarSeam.wrapCell(SEAM / GiantCraterField.CELL, GiantCraterField.CELL));
        }

        @Test
        void aCellInsideTheMoonRollsAsItself() {
            assertEquals(SEAM / GiantCraterField.CELL - 1,
                    LunarSeam.wrapCell(SEAM / GiantCraterField.CELL - 1, GiantCraterField.CELL));
        }

        @Test
        void anXOneWayAroundFromTheLightSideReadsAsTheLightSide() {
            assertFalse(Lunar.isDarkSide(LunarSeam.PERIOD + 1_000.0));
        }
    }

    @Nested
    class Footprints {

        @ParameterizedTest
        @ValueSource(ints = {SEAM, -SEAM})
        void aFootprintReachingAcrossTheSeamOnXIsRefused(int seam) {
            assertTrue(LunarSeam.crossesSeam(new BoundingBox(seam - 10, 0, 0, seam + 5, 10, 20)));
        }

        @ParameterizedTest
        @ValueSource(ints = {SEAM, -SEAM})
        void aFootprintReachingAcrossTheSeamOnZIsRefused(int seam) {
            assertTrue(LunarSeam.crossesSeam(new BoundingBox(0, 0, seam - 1, 20, 10, seam)));
        }

        @Test
        void aFootprintStoppingAtTheLastBlockBeforeTheSeamStands() {
            assertFalse(LunarSeam.crossesSeam(new BoundingBox(SEAM - 20, 0, -SEAM, SEAM - 1, 10, -SEAM + 20)));
        }

        @Test
        void aChunkWhoseFeaturesCouldWriteAcrossTheSeamGoesUndecorated() {
            assertFalse(LunarSeam.featuresMayDecorate(new ChunkPos(CHUNK_ENDING_AT_THE_SEAM, 0)));
        }

        @Test
        void aChunkPastTheSeamGoesUndecorated() {
            assertFalse(LunarSeam.featuresMayDecorate(new ChunkPos(0, -CHUNK_ENDING_AT_THE_SEAM - 2)));
        }

        @Test
        void aChunkAFeatureReachShortOfTheSeamIsDecorated() {
            assertTrue(LunarSeam.featuresMayDecorate(new ChunkPos(CHUNK_ENDING_AT_THE_SEAM - 1, 0)));
        }

        @Test
        void aStructureWithinAFeatureReachOfTheSeamIsRefused() {
            assertFalse(LunarSeam.structureMayStand(new BoundingBox(SEAM - 100, 0, 0, SEAM - 10, 40, 30)));
        }

        @Test
        void aStructureAFeatureReachShortOfTheSeamStands() {
            assertTrue(LunarSeam.structureMayStand(new BoundingBox(SEAM - 100, 0, 0,
                    SEAM - 1 - LunarSeam.FEATURE_REACH, 40, 30)));
        }
    }

    @Nested
    class CrossingShift {

        private static final int VIEW_DISTANCE = 4;
        private static final int SEAM_CHUNK = SEAM / 16;
        private static final double HEIGHT = 64.0;

        @Test
        void aCrossingOnXMovesEachHeldChunkOnePeriodOnX() {
            assertMovesTheHeldView(new Vec3(SEAM + 0.5, HEIGHT, 40.5), new LunarCrossingShift(
                    -LunarCrossingShift.PERIOD_CHUNKS, 0));
        }

        @Test
        void aCrossingOnZMovesEachHeldChunkOnePeriodOnZ() {
            assertMovesTheHeldView(new Vec3(40.5, HEIGHT, -SEAM - 0.5), new LunarCrossingShift(
                    0, LunarCrossingShift.PERIOD_CHUNKS));
        }

        @Test
        void aCrossingAtACornerMovesEachHeldChunkOnePeriodOnBoth() {
            assertMovesTheHeldView(new Vec3(-SEAM - 0.5, HEIGHT, SEAM + 0.5), new LunarCrossingShift(
                    LunarCrossingShift.PERIOD_CHUNKS, -LunarCrossingShift.PERIOD_CHUNKS));
        }

        /**
         * A body standing past the seam is carried across; the shift between where it stood and where it came out is
         * one period on each axis crossed, and the chunks it moves are exactly the landing view's chunks whose twin
         * the held view saw: the landing view less the ring the move adds.
         */
        private void assertMovesTheHeldView(Vec3 pastTheSeam, LunarCrossingShift expected) {
            Vec3 landed = LunarCrossing.across(pastTheSeam, Vec3.ZERO, 0.0F, 0.0F).orElseThrow().position();
            LunarCrossingShift shift = LunarCrossingShift.between(pastTheSeam, landed);
            assertEquals(expected, shift);
            ChunkPos stood = ChunkPos.containing(BlockPos.containing(pastTheSeam));
            ChunkTrackingView held = ChunkTrackingView.of(stood, VIEW_DISTANCE);
            ChunkTrackingView landing = ChunkTrackingView.of(ChunkPos.containing(BlockPos.containing(landed)),
                    VIEW_DISTANCE);
            Set<ChunkPos> moved = shift.moved(held, landing);
            Set<ChunkPos> landingLessTheAddedRing = new HashSet<>();
            landing.forEach(pos -> {
                if (held.contains(new ChunkPos(pos.x() - shift.chunksX(), pos.z() - shift.chunksZ()))) {
                    landingLessTheAddedRing.add(pos);
                }
            });
            assertTrue(moved.contains(shift.apply(stood)), "The chunk the body stood in should move with it");
            assertEquals(landingLessTheAddedRing, moved);
        }

        @Test
        void aHeldChunkAtTheSeamMovesToTheChunkAcrossIt() {
            assertEquals(new ChunkPos(-SEAM_CHUNK - 1, 0),
                    new LunarCrossingShift(-LunarCrossingShift.PERIOD_CHUNKS, 0).apply(new ChunkPos(SEAM_CHUNK - 1,
                            0)));
        }
    }
}
