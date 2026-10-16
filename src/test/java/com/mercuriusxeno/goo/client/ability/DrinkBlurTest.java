package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two overlapping spheres blur into one skin: across their join the blurred
 * depth has no crease and the normal the shade builds turns gently from
 * pixel to pixel, while a far wall beside them keeps its depth and the
 * silhouette against it stays sharp (decision
 * unmake-drink-draws-as-a-screen-space-fluid). The row is one line of
 * pixels through both spheres' middles, each pixel a fixed width of the
 * world at the spheres' depth.
 */
class DrinkBlurTest {

    /** Pixels a block spans on screen at the spheres' depth: a block two blocks away on a 1080-line screen. */
    private static final double PIXELS_PER_BLOCK = 200;
    private static final double RADIUS = 0.3;
    /** The spheres' middles across the row, in blocks: overlapping by half a radius each. */
    private static final double FIRST = 0;
    private static final double SECOND = 0.3;
    /** The spheres' middles' distance from the eye, in blocks. */
    private static final double SPHERES_AWAY = 2;
    /** The wall's distance from the eye, in blocks. */
    private static final double WALL_AWAY = 6;
    /** The row's ends across, in blocks. */
    private static final double ROW_FROM = -0.6;
    private static final double ROW_TO = 0.9;
    /** Blocks either side of the join the crease is looked for over. */
    private static final double ABOUT_THE_JOIN = 0.1;
    /** The most the blurred depth's second difference may be across the join, in blocks a pixel squared. */
    private static final double CREASE_BOUND = 1e-3;
    /** The most the normal may turn between neighbouring pixels across the join, in degrees. */
    private static final double TURN_BOUND = 1.5;
    private static final int TWO_PIXELS = 2;

    private static int pixelAt(double across) {
        return (int) Math.round((across - ROW_FROM) * PIXELS_PER_BLOCK);
    }

    private static double sphereDistance(double across, double middle) {
        double off = across - middle;
        return off * off < RADIUS * RADIUS ? SPHERES_AWAY - Math.sqrt(RADIUS * RADIUS - off * off) : WALL_AWAY;
    }

    private static double[][] row() {
        int pixels = pixelAt(ROW_TO) + 1;
        double[] distance = new double[pixels];
        double[] radius = new double[pixels];
        for (int pixel = 0; pixel < pixels; pixel++) {
            double across = ROW_FROM + pixel / PIXELS_PER_BLOCK;
            distance[pixel] = Math.min(sphereDistance(across, FIRST), sphereDistance(across, SECOND));
            radius[pixel] = distance[pixel] < WALL_AWAY ? RADIUS : 0;
        }
        return new double[][] {distance, radius};
    }

    private static double largestCrease(double[] distance) {
        double largest = 0;
        int join = pixelAt((FIRST + SECOND) / 2);
        int reach = (int) Math.round(ABOUT_THE_JOIN * PIXELS_PER_BLOCK);
        for (int pixel = join - reach; pixel <= join + reach; pixel++) {
            largest = Math.max(largest, Math.abs(distance[pixel + 1] - 2 * distance[pixel] + distance[pixel - 1]));
        }
        return largest;
    }

    private static double largestTurn(double[] distance) {
        double largest = 0;
        int join = pixelAt((FIRST + SECOND) / 2);
        int reach = (int) Math.round(ABOUT_THE_JOIN * PIXELS_PER_BLOCK);
        for (int pixel = join - reach; pixel <= join + reach; pixel++) {
            largest = Math.max(largest, Math.abs(angleAt(distance, pixel + 1) - angleAt(distance, pixel)));
        }
        return largest;
    }

    private static double angleAt(double[] distance, int pixel) {
        double slope = DrinkBlur.slopeAt(distance[pixel - 1], distance[pixel], distance[pixel + 1]);
        return Math.toDegrees(Math.atan(slope * PIXELS_PER_BLOCK));
    }

    @Test
    void twoOverlappingSpheresBlurIntoOneSkinWithNoCreaseAcrossTheirJoin() {
        double[][] row = row();

        double[] blurred = DrinkBlur.blurRow(row[0], row[1], PIXELS_PER_BLOCK);

        assertTrue(largestCrease(row[0]) > CREASE_BOUND, "unblurred, the join is a crease: " + largestCrease(row[0]));
        assertTrue(largestTurn(row[0]) > TURN_BOUND, "unblurred, the normal folds there: " + largestTurn(row[0]));
        assertTrue(largestCrease(blurred) < CREASE_BOUND, "the blurred crease: " + largestCrease(blurred));
        assertTrue(largestTurn(blurred) < TURN_BOUND, "the blurred turn, degrees: " + largestTurn(blurred));
    }

    @Test
    void theWallBesideTheSpheresKeepsItsDepth() {
        double[][] row = row();

        double[] blurred = DrinkBlur.blurRow(row[0], row[1], PIXELS_PER_BLOCK);

        for (int pixel = 0; pixel < blurred.length; pixel++) {
            if (row[1][pixel] == 0) {
                assertEquals(WALL_AWAY, blurred[pixel], "the wall at pixel " + pixel);
            } else {
                assertTrue(blurred[pixel] < WALL_AWAY - RADIUS, "the goo at pixel " + pixel + " stays off the wall");
            }
        }
    }

    @Test
    void aNeighbourPastTheDepthThresholdWeighsNothingAndTheKernelScalesWithTheSphere() {
        int taps = DrinkBlur.MOST_TAPS;
        double threshold = RADIUS * DrinkBlur.DEPTH_SHARE;

        assertEquals(0, DrinkBlur.weightOf(0, taps, threshold, threshold), "a neighbour at the threshold weighs nothing");
        assertEquals(1, DrinkBlur.weightOf(0, taps, 0, threshold));
        assertTrue(DrinkBlur.weightOf(taps, taps, 0, threshold) < DrinkBlur.weightOf(1, taps, 0, threshold));
        assertEquals(2 * DrinkBlur.widthOf(RADIUS, PIXELS_PER_BLOCK),
                DrinkBlur.widthOf(RADIUS, 2 * PIXELS_PER_BLOCK), 1e-9, "a sphere twice as near blurs twice as wide");
        assertEquals(TWO_PIXELS, DrinkBlur.tapsOf(TWO_PIXELS), "a narrow kernel reads every pixel");
        assertEquals(taps, DrinkBlur.tapsOf(PIXELS_PER_BLOCK), "a wide one spreads its capped taps");
    }

    @Test
    void theNormalTakesTheSmallerStepSoAnEdgePixelKeepsItsOwnSurface() {
        assertEquals(0.01, DrinkBlur.slopeAt(2.0, 2.01, WALL_AWAY), 1e-12);
        assertEquals(-0.01, DrinkBlur.slopeAt(WALL_AWAY, 2.01, 2.0), 1e-12);
    }
}
