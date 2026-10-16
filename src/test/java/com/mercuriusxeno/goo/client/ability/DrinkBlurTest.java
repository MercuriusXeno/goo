package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drink's blur makes splatted spheres one skin: two overlapping spheres
 * blur with no crease across their join, a lone sphere's face blurs with no
 * terraces, a far wall beside them keeps its depth, and a near sphere over a
 * far one leaves no lines hanging down the screen (decision
 * unmake-drink-draws-as-a-screen-space-fluid). Each scene is an image of
 * spheres before a wall, each pixel a fixed width of the world at the
 * spheres' depth, blurred as drink_blur.fsh blurs it.
 */
class DrinkBlurTest {

    /** Pixels a block spans on screen at the spheres' depth: a block four blocks away on a 1080-line screen. */
    private static final double PIXELS_PER_BLOCK = 100;
    /** The spheres' middles' distance from the eye, in blocks. */
    private static final double SPHERES_AWAY = 4;
    /** Pixels a block spans a block from the eye, so it spans {@link #PIXELS_PER_BLOCK} at the spheres' depth. */
    private static final double FOCAL = PIXELS_PER_BLOCK * SPHERES_AWAY;
    /** The wall's distance from the eye, in blocks. */
    private static final double WALL_AWAY = 8;
    /** A sphere's radius, its disc under the blur's cap at this depth. */
    private static final double RADIUS = 0.1;
    /** Blocks the square image spans either way. */
    private static final double IMAGE_BLOCKS = 0.6;
    /** Blocks either side of a join or a face's middle a crease or a terrace is looked for over. */
    private static final double LOOKED_OVER = 0.06;
    /** The most the blurred depth's second difference may be across the join, in blocks a pixel squared. */
    private static final double CREASE_BOUND = 2e-3;
    /** The most the normal may turn between neighbouring pixels across the join, in degrees. */
    private static final double TURN_BOUND = 4;
    /** The near sphere's radius and its middle's offset from the far sphere's, across and down the screen. */
    private static final double NEAR_RADIUS = 0.07;
    private static final double NEAR_ACROSS = 0.04;
    private static final double NEAR_DOWN = 0.05;
    /** Degrees past the spheres' own turning a column's normal may tilt from its neighbour's without a line. */
    private static final double STREAK_BOUND = 2;
    /** The most rows a line may hang down the blurred skin. */
    private static final int LONGEST_LINE = 3;
    /** A thin stream's radius, whose disc stays under the cap. */
    private static final double THREAD_RADIUS = 0.03;
    private static final double HALF = 0.5;
    private static final double DELTA = 1e-9;

    /**
     * An image of spheres before the wall.
     *
     * @param distance each pixel's distance from the eye
     * @param radius   each pixel's frontmost sphere's radius, 0 for the wall
     */
    private record Scene(double[][] distance, double[][] radius) {

        int size() {
            return distance.length;
        }

        int middle() {
            return size() / 2;
        }
    }

    /**
     * @param spheres each sphere as its middle across and down the screen in blocks, its extra distance from the eye
     *                past {@link #SPHERES_AWAY} and its radius
     * @return the image, the frontmost sphere at each pixel or the wall
     */
    private static Scene sceneOf(double[]... spheres) {
        int size = (int) Math.round(IMAGE_BLOCKS * PIXELS_PER_BLOCK);
        double[][] distance = new double[size][size];
        double[][] radius = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                distance[row][column] = WALL_AWAY;
                for (double[] sphere : spheres) {
                    double across = (column - size / 2.0) / PIXELS_PER_BLOCK - sphere[0];
                    double down = (row - size / 2.0) / PIXELS_PER_BLOCK - sphere[1];
                    double off = across * across + down * down;
                    double surface = SPHERES_AWAY + sphere[2] - Math.sqrt(Math.max(0, sphere[3] * sphere[3] - off));
                    if (off < sphere[3] * sphere[3] && surface < distance[row][column]) {
                        distance[row][column] = surface;
                        radius[row][column] = sphere[3];
                    }
                }
            }
        }
        return new Scene(distance, radius);
    }

    private static int pixelAt(double across) {
        return (int) Math.round(across * PIXELS_PER_BLOCK + IMAGE_BLOCKS * PIXELS_PER_BLOCK / 2);
    }

    private static double acrossAngle(double[][] image, int row, int column) {
        return Math.toDegrees(Math.atan(DrinkBlur.slopeAt(image[row][column - 1], image[row][column],
                image[row][column + 1]) * PIXELS_PER_BLOCK));
    }

    private static double downAngle(double[][] image, int row, int column) {
        return Math.toDegrees(Math.atan(DrinkBlur.slopeAt(image[row - 1][column], image[row][column],
                image[row + 1][column]) * PIXELS_PER_BLOCK));
    }

    /**
     * @param image  a scene's distances
     * @param row    the row looked along
     * @param middle the column looked about
     * @return the most the normal turns between neighbouring pixels along the row within {@link #LOOKED_OVER}
     */
    private static double largestTurn(double[][] image, int row, int middle) {
        int reach = (int) Math.round(LOOKED_OVER * PIXELS_PER_BLOCK);
        double largest = 0;
        for (int column = middle - reach; column <= middle + reach; column++) {
            largest = Math.max(largest, Math.abs(acrossAngle(image, row, column + 1) - acrossAngle(image, row, column)));
        }
        return largest;
    }

    private static double largestCrease(double[][] image, int row, int middle) {
        int reach = (int) Math.round(LOOKED_OVER * PIXELS_PER_BLOCK);
        double largest = 0;
        for (int column = middle - reach; column <= middle + reach; column++) {
            largest = Math.max(largest,
                    Math.abs(image[row][column + 1] - 2 * image[row][column] + image[row][column - 1]));
        }
        return largest;
    }

    @Test
    void twoOverlappingSpheresBlurIntoOneSkinWithNoCreaseAcrossTheirJoin() {
        Scene scene = sceneOf(new double[] {-RADIUS / 2, 0, 0, RADIUS}, new double[] {RADIUS / 2, 0, 0, RADIUS});
        int join = scene.middle();

        double[][] blurred = DrinkBlur.blurImage(scene.distance(), scene.radius(), FOCAL);

        double rawTurn = largestTurn(scene.distance(), join, join);
        double turn = largestTurn(blurred, join, join);
        assertTrue(largestCrease(scene.distance(), join, join) > CREASE_BOUND, "unblurred, the join is a crease");
        assertTrue(rawTurn > TURN_BOUND, "unblurred, the normal folds there: " + rawTurn);
        assertTrue(largestCrease(blurred, join, join) < CREASE_BOUND,
                "the blurred crease: " + largestCrease(blurred, join, join));
        assertTrue(turn < TURN_BOUND, "the blurred turn, degrees: " + turn);
    }

    /**
     * A lone sphere blurs with no terraces: the blurred normal turns no
     * faster anywhere on its face than the sphere's own does, where a kernel
     * whose taps stepped as it moved stepped the depth along every line of
     * equal depth, contour rings over the skin.
     */
    @Test
    void aLoneSpheresFaceBlursWithNoTerraces() {
        Scene scene = sceneOf(new double[] {0, 0, 0, RADIUS});
        int middle = scene.middle();

        double[][] blurred = DrinkBlur.blurImage(scene.distance(), scene.radius(), FOCAL);

        double raw = largestTurn(scene.distance(), middle, middle);
        double smoothed = largestTurn(blurred, middle, middle);
        assertTrue(smoothed <= raw, "the blurred face turns at most " + smoothed + " degrees a pixel against the "
                + "sphere's own " + raw);
    }

    @Test
    void theWallBesideTheSpheresKeepsItsDepth() {
        Scene scene = sceneOf(new double[] {-RADIUS / 2, 0, 0, RADIUS}, new double[] {RADIUS / 2, 0, 0, RADIUS});

        double[][] blurred = DrinkBlur.blurImage(scene.distance(), scene.radius(), FOCAL);

        for (int row = 0; row < scene.size(); row++) {
            for (int column = 0; column < scene.size(); column++) {
                if (scene.radius()[row][column] == 0) {
                    assertEquals(WALL_AWAY, blurred[row][column], "the wall at " + row + ", " + column);
                } else {
                    assertTrue(blurred[row][column] < WALL_AWAY - RADIUS, "the goo stays off the wall");
                }
            }
        }
    }

    /**
     * A near sphere in front of a far one blurs with no lines hanging down
     * the screen: nowhere does the normal's tilt down the screen jump between
     * neighbouring columns, beyond the spheres' own turning, for more than
     * {@link #LONGEST_LINE} rows running, where a blur run across then down
     * left a line under the near sphere's rim in one column and not the next.
     */
    @Test
    void aNearSphereOverAFarOneBlursWithNoLinesHangingDownTheScreen() {
        Scene scene = sceneOf(new double[] {0, 0, RADIUS / 2, RADIUS},
                new double[] {NEAR_ACROSS, NEAR_DOWN, 0, NEAR_RADIUS});

        double[][] blurred = DrinkBlur.blurImage(scene.distance(), scene.radius(), FOCAL);

        int longest = 0;
        for (int column = 1; column < scene.size() - 2; column++) {
            int run = 0;
            for (int row = 1; row < scene.size() - 1; row++) {
                run = hangsALine(scene, blurred, row, column) ? run + 1 : 0;
                longest = Math.max(longest, run);
            }
        }
        assertTrue(longest <= LONGEST_LINE, "a line hangs " + longest + " rows down the blurred skin");
    }

    private static boolean hangsALine(Scene scene, double[][] blurred, int row, int column) {
        double[][] radius = scene.radius();
        boolean covered = radius[row - 1][column] > 0 && radius[row][column] > 0 && radius[row + 1][column] > 0
                && radius[row][column + 1] > 0 && radius[row - 1][column + 1] > 0 && radius[row + 1][column + 1] > 0;
        return covered && Math.abs(downAngle(blurred, row, column + 1) - downAngle(blurred, row, column))
                > STREAK_BOUND + Math.abs(downAngle(scene.distance(), row, column + 1)
                - downAngle(scene.distance(), row, column));
    }

    @Test
    void aNeighbourPastTheDepthThresholdWeighsNothingAndTheDiscScalesWithTheSphere() {
        double threshold = RADIUS * DrinkBlur.DEPTH_SHARE;

        assertEquals(0, DrinkBlur.weightOf(0, threshold, threshold), "a neighbour at the threshold weighs nothing");
        assertEquals(0, DrinkBlur.weightOf(1, 0, threshold), "nor one at the disc's edge, so a new pixel enters at nothing");
        assertEquals(1, DrinkBlur.weightOf(0, 0, threshold));
        assertTrue(DrinkBlur.weightOf(HALF, 0, threshold) < DrinkBlur.weightOf(0, 0, threshold));
        assertEquals(2 * DrinkBlur.widthOf(THREAD_RADIUS, PIXELS_PER_BLOCK),
                DrinkBlur.widthOf(THREAD_RADIUS, 2 * PIXELS_PER_BLOCK), DELTA, "a sphere twice as near blurs twice as wide");
        assertEquals(DrinkBlur.MOST_REACH, DrinkBlur.widthOf(1, PIXELS_PER_BLOCK), DELTA, "a wide one is capped");
    }

    @Test
    void theNormalTakesTheSmallerStepSoAnEdgePixelKeepsItsOwnSurface() {
        assertEquals(0.01, DrinkBlur.slopeAt(2.0, 2.01, WALL_AWAY), 1e-12);
        assertEquals(-0.01, DrinkBlur.slopeAt(WALL_AWAY, 2.01, 2.0), 1e-12);
    }
}
