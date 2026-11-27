package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
            assertEquals(-SEAM / CraterField.CELL, LunarSeam.wrapCell(SEAM / CraterField.CELL, CraterField.CELL));
        }

        @Test
        void aCellInsideTheMoonRollsAsItself() {
            assertEquals(SEAM / CraterField.CELL - 1,
                    LunarSeam.wrapCell(SEAM / CraterField.CELL - 1, CraterField.CELL));
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
}
