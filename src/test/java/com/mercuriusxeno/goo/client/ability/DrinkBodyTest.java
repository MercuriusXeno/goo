package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block becomes its stream as the width of one skin: a cube where it
 * stood at the first frame, softening into a rounded blob and shrinking
 * smoothly about its middle as it drains, a funnel narrowing from its width
 * to the stream's in front of it, nothing jumping at the start
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkBodyTest {

    private static final double DELTA = 1e-6;
    private static final long SEED = 42;
    private static final double NOW = 100;
    private static final double STREAM = 0.05;
    private static final double HALFWAY = 0.5;
    private static final double NEAR_THE_END = 0.95;
    private static final double STEP = 0.02;

    @Test
    void theBlobIsTheWholeCubeAtTheStartAndGoneAtTheEndShrinkingSmoothly() {
        assertEquals(1, DrinkBody.sizeAt(0), DELTA);
        assertEquals(0, DrinkBody.sizeAt(1), DELTA);
        assertTrue(DrinkBody.sizeAt(STEP) > 1 - STEP, "no jump at the start");
        double before = 1;
        for (double progress = 0; progress <= 1; progress += STEP) {
            double size = DrinkBody.sizeAt(progress);
            assertTrue(size <= before + DELTA, "shrinks only");
            before = size;
        }
    }

    @Test
    void theCubeSoftensIntoALiquidBlobOverTheFirstPartOfTheDrain() {
        assertEquals(0, DrinkBody.liquidityAt(0), DELTA);
        assertEquals(1, DrinkBody.liquidityAt(DrinkBody.LIQUEFY), DELTA);
        assertEquals(1, DrinkBody.liquidityAt(1), DELTA);
        assertEquals(0, DrinkBody.roundnessAt(DrinkBody.CENTER, 0), DELTA);
        assertEquals(1, DrinkBody.roundnessAt(DrinkBody.CENTER, DrinkBody.LIQUEFY), DELTA);
    }

    @Test
    void theCubeStandsWhereItStoodAtTheStartWithSharpEndsAndItsFullWidth() {
        assertEquals(DrinkBody.MOUTH, DrinkBody.blobAt(DrinkBody.CENTER, 0), DELTA);
        assertTrue(DrinkBody.blobAt(NEAR_THE_END * DrinkStream.BLOCK_SPAN, 0) > DrinkBody.MOUTH * 0.9,
                "the cube keeps its width nearly to its ends");
        assertEquals(0, DrinkBody.blobAt(0, 0), DELTA);
        assertEquals(0, DrinkBody.blobAt(DrinkStream.BLOCK_SPAN, 0), DELTA);
        assertEquals(DrinkBody.MOUTH, DrinkBody.widthAt(DrinkBody.CENTER, 0, STREAM, SEED, NOW), DELTA);
    }

    @Test
    void theBlobRoundsAndShrinksAboutItsMiddleAsItDrains() {
        double size = DrinkBody.sizeAt(HALFWAY);

        assertEquals(DrinkBody.MOUTH * size, DrinkBody.blobAt(DrinkBody.CENTER, HALFWAY), DELTA);
        assertEquals(0, DrinkBody.blobAt(DrinkBody.CENTER - DrinkBody.CENTER * size, HALFWAY), DELTA);
        assertTrue(DrinkBody.blobAt(DrinkBody.CENTER - DrinkBody.CENTER * size * NEAR_THE_END, HALFWAY)
                < DrinkBody.MOUTH * size * 0.5, "a round blob narrows well before its end");
    }

    @Test
    void theFunnelNarrowsFromTheBlobsWidthToTheStreamsInFrontOfIt() {
        double past = DrinkBody.CENTER + DrinkBody.FUNNEL;

        assertEquals(0, DrinkBody.funnelAt(0, 0, STREAM), DELTA);
        assertEquals(DrinkBody.MOUTH, DrinkBody.funnelAt(DrinkBody.CENTER, 0, STREAM), DELTA);
        assertEquals(STREAM, DrinkBody.funnelAt(past, 0, STREAM), DELTA);
        assertEquals(STREAM, DrinkBody.widthAt(past + 1, 0, STREAM, SEED, NOW), DELTA);
        assertEquals(STREAM, DrinkBody.widthAt(past + 1, 1, STREAM, SEED, NOW), DELTA);
        double between = DrinkBody.funnelAt(DrinkBody.CENTER + DrinkBody.FUNNEL / 2, 0, STREAM);
        assertTrue(between < DrinkBody.MOUTH && between > STREAM);
        assertEquals(1, DrinkBody.roundnessAt(past, 0), DELTA);
    }

    @Test
    void theLiquidBlobWobblesWithinItsBound() {
        double still = DrinkBody.widthAt(DrinkBody.CENTER, 0, STREAM, SEED, NOW);
        double liquid = DrinkBody.widthAt(DrinkBody.CENTER, DrinkBody.LIQUEFY, STREAM, SEED, NOW);
        double expected = DrinkBody.MOUTH * DrinkBody.sizeAt(DrinkBody.LIQUEFY);

        assertEquals(DrinkBody.MOUTH, still, DELTA);
        assertTrue(Math.abs(liquid - expected) <= expected * DrinkBody.WOBBLE + DELTA);
    }
}
