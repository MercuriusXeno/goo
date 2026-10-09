package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream flows languidly along its path, kept the longest route's travel
 * at the base pace past its drain; between the ends it snakes off the
 * straight line and wanders, its width a slow profile of bulbs and waists
 * with gentle grades between, scaled by its block, its texture riding the
 * flow, tapering to a point at both ends (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkStreamTest {

    private static final double DELTA = 1e-9;
    private static final long START = 100;
    private static final long END = 134;
    private static final DrinkPayload.Streaming BLOCK = new DrinkPayload.Streaming(BlockPos.ZERO, START, END);
    private static final Vec3 FROM = new Vec3(7, 2.5, 3.5);
    private static final Vec3 TO = new Vec3(1.5, 2.2, 3.1);
    private static final long SEED = 42;
    private static final DrinkStream.Path PATH = new DrinkStream.Path(FROM, TO, SEED);
    private static final double LATER = 100;
    private static final double LENGTH = 3;
    private static final double MIDDLE = 1.5;
    private static final int TICKS_A_SECOND = 20;
    private static final double FOUR_BLOCKS = 4;
    private static final double OFF_THE_LINE = 0.02;
    /** Blocks of liquid the width is read over, both ways from the start. */
    private static final double READ = 40;
    private static final double STEP = 0.05;
    /** The steepest a grade between bulb and waist may run, in blocks of radius a block of liquid. */
    private static final double GENTLE = 0.3;
    /** The fewest times the width crosses its middle over the read, so the stream undulates. */
    private static final int UNDULATIONS = 20;
    private static final double LEAST_RATIO = 2;
    private static final double MOST_RATIO = 4;
    private static final double RADIUS = 0.1;
    private static final double NUDGE = 0.01;

    @Nested
    class Timing {

        @Test
        void aStreamIsKeptTheLongestRoutesTravelPastItsDrain() {
            assertFalse(DrinkStream.gone(BLOCK, END + DrinkStream.LONGEST_TRAVEL_TICKS - 1));
            assertTrue(DrinkStream.gone(BLOCK, END + DrinkStream.LONGEST_TRAVEL_TICKS));
        }

        @Test
        void theFlowIsFourBlocksASecondAndTheLongestRouteOutlastsTheCone() {
            double longestRoute = SiphonRule.RANGE + DrinkStream.GLOVE_SLACK;
            assertEquals(FOUR_BLOCKS, DrinkStream.FLOW * TICKS_A_SECOND, DELTA);
            assertTrue(DrinkStream.LONGEST_TRAVEL_TICKS * DrinkStream.FLOW >= longestRoute);
        }
    }

    @Nested
    class Path {

        @Test
        void theStreamLeavesThePathsStartAndReachesItsEnd() {
            assertEquals(0, DrinkStream.pointAt(PATH, 0, START).distanceTo(FROM), DELTA);
            assertEquals(0, DrinkStream.pointAt(PATH, 1, START).distanceTo(TO), DELTA);
        }

        @Test
        void theStreamSnakesOffTheLineAndWanders() {
            Vec3 straight = FROM.lerp(TO, 0.5);
            Vec3 bent = DrinkStream.pointAt(PATH, 0.5, START);
            Vec3 later = DrinkStream.pointAt(PATH, 0.5, START + LATER);

            assertTrue(bent.distanceTo(straight) > OFF_THE_LINE, "the middle leaves the straight line");
            assertTrue(bent.distanceTo(later) > OFF_THE_LINE, "the bend moves with time");
            assertTrue(bent.distanceTo(straight) <= 2 * DrinkStream.SNAKE + DELTA);
        }

        @Test
        void theNearestShareIsWhereThePointProjectsOntoTheLine() {
            Vec3 beside = TO.subtract(FROM).cross(new Vec3(0, 1, 0)).normalize();

            assertEquals(0.5, PATH.nearestShare(FROM.lerp(TO, 0.5).add(beside)), DELTA);
            assertEquals(0, PATH.nearestShare(FROM.add(1, 0, 0)), DELTA);
            assertEquals(1, PATH.nearestShare(TO.subtract(1, 0, 0)), DELTA);
        }

        @Test
        void theTextureMirrorsEveryBlockWithNoSeam() {
            assertEquals(0, DrinkStream.textureU(0), DELTA);
            assertEquals(1, DrinkStream.textureU(1), DELTA);
            assertEquals(0, DrinkStream.textureU(2), DELTA);
            assertEquals(DrinkStream.textureU(0.999), DrinkStream.textureU(1.001), 1e-2);
        }

        @Test
        void theTextureIsLaidOverTheWorldAtItsOwnSizeMirroredWithNoSeam() {
            assertEquals(0, DrinkStream.textureAt(0), DELTA);
            assertEquals(1, DrinkStream.textureAt(1), DELTA);
            assertEquals(0, DrinkStream.textureAt(2), DELTA);
            assertEquals(DrinkStream.textureAt(3.999), DrinkStream.textureAt(4.001), 1e-2);
            assertEquals(DrinkStream.textureAt(0.25), DrinkStream.textureAt(-0.25), DELTA);
        }
    }

    @Nested
    class Shape {

        @Test
        void theStreamTapersToAPointAtBothEndsAndIsThickBetween() {
            assertEquals(0, DrinkStream.radiusAt(1, 0, 0, 0, LENGTH, SEED), DELTA);
            assertEquals(0, DrinkStream.radiusAt(1, 0, LENGTH, 0, LENGTH, SEED), DELTA);
            assertTrue(DrinkStream.radiusAt(1, 0, MIDDLE, 0, LENGTH, SEED) >= DrinkStream.WAIST - DELTA);
            assertEquals(0, DrinkStream.radiusAt(1, 0, LENGTH + 1, 0, LENGTH, SEED), DELTA);
            assertEquals(1, DrinkStream.taperAt(MIDDLE, 0, LENGTH), DELTA);
            assertEquals(0, DrinkStream.taperAt(0, 0, LENGTH), DELTA);
        }

        @Test
        void theWidthScalesWithTheStream() {
            double alone = DrinkStream.radiusAt(1, MIDDLE, MIDDLE, 0, LENGTH, SEED);

            assertEquals(2 * alone, DrinkStream.radiusAt(2, MIDDLE, MIDDLE, 0, LENGTH, SEED), DELTA);
        }

        @Test
        void theWidthRunsBetweenTheWaistAndTheBulbAndReachesBoth() {
            double least = Double.MAX_VALUE;
            double most = 0;
            for (double material = -READ; material <= READ; material += STEP) {
                double width = DrinkStream.widthAt(material, SEED);
                least = Math.min(least, width);
                most = Math.max(most, width);
            }

            assertEquals(DrinkStream.WAIST, least, DELTA);
            assertEquals(DrinkStream.BULB, most, DELTA);
            assertTrue(DrinkStream.BULB / DrinkStream.WAIST >= LEAST_RATIO
                    && DrinkStream.BULB / DrinkStream.WAIST <= MOST_RATIO);
        }

        @Test
        void theWidthUndulatesAlongTheLiquidWithGentleGrades() {
            double middle = (DrinkStream.WAIST + DrinkStream.BULB) / 2;
            int crossings = 0;
            double steepest = 0;
            boolean wide = DrinkStream.widthAt(-READ, SEED) > middle;
            for (double material = -READ; material <= READ; material += STEP) {
                double width = DrinkStream.widthAt(material, SEED);
                crossings += width > middle != wide ? 1 : 0;
                wide = width > middle;
                steepest = Math.max(steepest, Math.abs(DrinkStream.widthAt(material + STEP, SEED) - width) / STEP);
            }

            assertTrue(crossings >= UNDULATIONS, "crossed the middle " + crossings + " times");
            assertTrue(steepest <= GENTLE, "steepest grade " + steepest);
        }

        @Test
        void aRingFlowsAlongThePathWithItsRadiusAtItsPace() {
            DrinkStream.Ring ring = DrinkStream.ring(PATH, 0.5, START, RADIUS, 0, 0.5, 2 * DrinkStream.FLOW);
            Vec3 flow = DrinkStream.pointAt(PATH, 0.5 + NUDGE, START).subtract(DrinkStream.pointAt(PATH, 0.5 - NUDGE,
                    START));

            assertEquals(0, ring.flow().distanceTo(flow.normalize()), 1e-3);
            assertEquals(1, ring.flow().length(), 1e-6);
            assertEquals(RADIUS, ring.radius(), DELTA);
            assertEquals(2 * DrinkStream.FLOW, ring.speed(), DELTA);
            assertEquals(0, ring.center().distanceTo(DrinkStream.pointAt(PATH, 0.5, START)), DELTA);
        }

        @Test
        void aPathWithAnArrivalLeavesStraightAndLandsAlongIt() {
            Vec3 arrival = new Vec3(0, 0, -1);
            DrinkStream.Path curved = new DrinkStream.Path(FROM, TO, SEED, arrival);
            Vec3 landing = curved.spineAt(1).subtract(curved.spineAt(1 - NUDGE)).normalize();
            Vec3 leaving = curved.spineAt(NUDGE).subtract(curved.spineAt(0)).normalize();

            assertEquals(FROM, curved.spineAt(0));
            assertEquals(TO, curved.spineAt(1));
            assertTrue(landing.dot(arrival) > 0.99, "lands along the arrival, dot " + landing.dot(arrival));
            assertTrue(leaving.dot(TO.subtract(FROM).normalize()) > 0.99, "leaves straight for the end");
            assertEquals(0, DrinkStream.pointAt(curved, 1, START).distanceTo(TO), DELTA);
        }
    }
}
