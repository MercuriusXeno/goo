package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block's stream flows languidly from the block's far side until it enters
 * the glove, and once the block is drained its tail follows the rest in at
 * the same pace; between the ends it snakes off the straight line and
 * wanders, its width a slow profile of bulbs and waists with gentle grades
 * between, its texture riding the flow, tapering to a point at both ends
 * (decision unmake-waves-dissolve-by-crucible-cost).
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
    private static final DrinkStream.Path STRAIGHT = new DrinkStream.Path(FROM, FROM.add(LENGTH, 0, 0), SEED);
    /** A way five blocks long, so the flow covers a tenth of it a second. */
    private static final double WAY = 5;
    /** Ticks the flow takes over half the way. */
    private static final double HALF_WAY_TICKS = WAY / 2 / DrinkStream.FLOW;
    private static final int TICKS_A_SECOND = 20;
    private static final double TWO_BLOCKS = 2;
    /** A span wide enough that no ring between 0 and 1 tapers. */
    private static final DrinkStream.Span UNTAPERED = new DrinkStream.Span(-1, 2);
    private static final DrinkStream.Span WHOLE = new DrinkStream.Span(0, 1);
    private static final double OFF_THE_LINE = 0.02;
    /** Blocks of liquid the width is read over, both ways from the start. */
    private static final double READ = 40;
    private static final double STEP = 0.05;
    /** The steepest a grade between bulb and waist may run, in blocks of radius a block of liquid. */
    private static final double GENTLE = 0.5;
    /** The least of the liquid that sits at the bulb or at the waist. */
    private static final double MOSTLY = 0.25;
    private static final double LEAST_RATIO = 3;
    private static final double MOST_RATIO = 4;
    private static final double TWO_PI = 2 * Math.PI;

    @Nested
    class Timing {

        @Test
        void theHeadRunsOutAtTheFlowsPaceAndEntersTheGloveAfterTheWay() {
            assertTrue(DrinkStream.span(BLOCK, START, WAY).isEmpty());
            DrinkStream.Span halfway = DrinkStream.span(BLOCK, START + HALF_WAY_TICKS, WAY);
            assertEquals(0.5, halfway.head(), DELTA);
            assertEquals(0, halfway.tail(), DELTA);
            assertEquals(1, DrinkStream.span(BLOCK, START + 2 * HALF_WAY_TICKS, WAY).head(), DELTA);
        }

        @Test
        void theTailLeavesOnceDrainedAndTheStreamIsKeptTheLongestWaysTravel() {
            assertEquals(0, DrinkStream.span(BLOCK, END, WAY).tail(), DELTA);
            assertEquals(0.5, DrinkStream.span(BLOCK, END + HALF_WAY_TICKS, WAY).tail(), DELTA);
            assertTrue(DrinkStream.span(BLOCK, END + 2 * HALF_WAY_TICKS, WAY).isEmpty());
            assertFalse(DrinkStream.gone(BLOCK, END + DrinkStream.LONGEST_TRAVEL_TICKS - 1));
            assertTrue(DrinkStream.gone(BLOCK, END + DrinkStream.LONGEST_TRAVEL_TICKS));
        }

        @Test
        void theFlowIsTwoBlocksASecondAndTheLongestWayOutlastsTheCone() {
            assertEquals(TWO_BLOCKS, DrinkStream.FLOW * TICKS_A_SECOND, DELTA);
            double longestWay = SiphonRule.RANGE + DrinkStream.GLOVE_SLACK;
            assertTrue(DrinkStream.LONGEST_TRAVEL_TICKS * DrinkStream.FLOW >= longestWay);
        }
    }

    @Nested
    class Path {

        @Test
        void theStreamLeavesTheFarSideAndEntersTheGlove() {
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
        void theLiquidFlowsTowardTheGlove() {
            double before = DrinkStream.materialAt(0.5, LENGTH, START);
            double after = DrinkStream.materialAt(0.5, LENGTH, START + 1);

            assertEquals(-DrinkStream.FLOW, after - before, DELTA);
            assertEquals(LENGTH, DrinkStream.materialAt(1, LENGTH, 0) - DrinkStream.materialAt(0, LENGTH, 0), DELTA);
        }

        @Test
        void theTextureMirrorsEveryBlockWithNoSeam() {
            assertEquals(0, DrinkStream.textureU(0), DELTA);
            assertEquals(1, DrinkStream.textureU(1), DELTA);
            assertEquals(0, DrinkStream.textureU(2), DELTA);
            assertEquals(DrinkStream.textureU(0.999), DrinkStream.textureU(1.001), 1e-2);
        }

        @Test
        void theMoltenTextureIsPulledAboutButStaysOnTheSpriteAndWrapsWithNoSeam() {
            double material = 1.3;
            double angle = 0.7;
            float u = DrinkStream.moltenU(material, angle, START, SEED);
            float v = DrinkStream.moltenV(material, angle, START, SEED);

            assertTrue(u >= 0 && u <= 1 && v >= 0 && v <= 1);
            assertTrue(Math.abs(u - DrinkStream.textureU(material)) <= DrinkStream.TEXTURE_WARP + DELTA);
            assertTrue(u != DrinkStream.textureU(material) || v != angle / TWO_PI, "the warp moves the texture");
            assertEquals(DrinkStream.moltenV(material, 0, START, SEED), DrinkStream.moltenV(material, TWO_PI, START,
                    SEED), 1e-6);
        }
    }

    @Nested
    class Shape {

        @Test
        void theRingsRunFromTheTailToTheHeadTenToTheBlock() {
            List<DrinkStream.Ring> rings = DrinkStream.rings(STRAIGHT, WHOLE, 0, START);

            assertEquals((int) (LENGTH * DrinkStream.RINGS_PER_BLOCK) + 1, rings.size());
            assertEquals(0, rings.getFirst().center().distanceTo(FROM), DELTA);
            assertEquals(0, rings.getLast().center().distanceTo(FROM.add(LENGTH, 0, 0)), DELTA);
            assertTrue(DrinkStream.rings(PATH, new DrinkStream.Span(1, 1), 0, START).isEmpty());
        }

        @Test
        void theBlocksOwnSpanIsLeftToTheBlockWhileItTurns() {
            double lowest = DrinkStream.BLOCK_SPAN / LENGTH;
            List<DrinkStream.Ring> rings = DrinkStream.rings(STRAIGHT, WHOLE, lowest, START);

            assertEquals(lowest, rings.getFirst().share(), DELTA);
            assertEquals(1, rings.getLast().share(), DELTA);
            assertEquals((int) Math.ceil(LENGTH * DrinkStream.RINGS_PER_BLOCK * (1 - lowest)) + 1, rings.size());
        }

        @Test
        void theStreamTapersToAPointAtBothEndsAndIsThickBetween() {
            assertEquals(0, DrinkStream.radiusAt(0, WHOLE, 0, LENGTH, SEED), DELTA);
            assertEquals(0, DrinkStream.radiusAt(1, WHOLE, 0, LENGTH, SEED), DELTA);
            assertTrue(DrinkStream.radiusAt(0.5, WHOLE, 0, LENGTH, SEED) >= DrinkStream.WAIST - DELTA);
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
        void theWidthSitsMostlyAtTheBulbOrTheWaistWithGentleGradesBetween() {
            int samples = 0;
            int atAnEnd = 0;
            double steepest = 0;
            for (double material = -READ; material <= READ; material += STEP) {
                double width = DrinkStream.widthAt(material, SEED);
                samples++;
                atAnEnd += width <= DrinkStream.WAIST + DELTA || width >= DrinkStream.BULB - DELTA ? 1 : 0;
                steepest = Math.max(steepest, Math.abs(DrinkStream.widthAt(material + STEP, SEED) - width) / STEP);
            }

            assertTrue((double) atAnEnd / samples >= MOSTLY, "at an end for " + atAnEnd + " of " + samples);
            assertTrue(steepest <= GENTLE, "steepest grade " + steepest);
        }

        @Test
        void aRingsFrameIsRightHandedAboutTheFlow() {
            List<DrinkStream.Ring> rings = DrinkStream.rings(PATH, UNTAPERED, 0, START);
            DrinkStream.Ring ring = rings.get(rings.size() / 2);
            Vec3 flow = rings.get(rings.size() / 2 + 1).center().subtract(rings.get(rings.size() / 2 - 1).center());

            assertTrue(ring.side().cross(ring.across()).dot(flow) > 0);
            assertEquals(0, ring.side().dot(ring.across()), 1e-6);
            assertEquals(1, ring.outAt(0).length(), 1e-6);
        }
    }
}
