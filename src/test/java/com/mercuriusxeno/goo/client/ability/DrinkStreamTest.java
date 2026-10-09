package com.mercuriusxeno.goo.client.ability;

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
 * A block's stream runs out from its face until it enters the glove, and
 * once the block is drained its tail follows the rest in; between the ends it
 * snakes off the straight line and wanders, its liquid sliding toward the
 * glove, swelling, pinching and tapering to a point at both ends
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
    private static final double LATER = 40;
    private static final double LENGTH = 3;
    /** A span wide enough that no ring between 0 and 1 tapers. */
    private static final DrinkStream.Span UNTAPERED = new DrinkStream.Span(-1, 2);
    private static final double OFF_THE_LINE = 0.02;

    @Nested
    class Timing {

        @Test
        void theHeadRunsOutFromTheBlockAndEntersTheGloveAfterTheTravel() {
            assertTrue(DrinkStream.span(BLOCK, START).isEmpty());
            DrinkStream.Span halfway = DrinkStream.span(BLOCK, START + DrinkStream.TRAVEL_TICKS / 2.0);
            assertEquals(0.5, halfway.head(), DELTA);
            assertEquals(0, halfway.tail(), DELTA);
            assertEquals(1, DrinkStream.span(BLOCK, START + DrinkStream.TRAVEL_TICKS).head(), DELTA);
        }

        @Test
        void theTailLeavesTheBlockOnceItIsDrainedAndTheStreamIsGoneWhenItEnters() {
            assertEquals(0, DrinkStream.span(BLOCK, END).tail(), DELTA);
            assertEquals(0.5, DrinkStream.span(BLOCK, END + DrinkStream.TRAVEL_TICKS / 2.0).tail(), DELTA);
            assertFalse(DrinkStream.gone(BLOCK, END + DrinkStream.TRAVEL_TICKS - 1));
            assertTrue(DrinkStream.gone(BLOCK, END + DrinkStream.TRAVEL_TICKS));
            assertTrue(DrinkStream.span(BLOCK, END + DrinkStream.TRAVEL_TICKS).isEmpty());
        }
    }

    @Nested
    class Path {

        @Test
        void theStreamLeavesTheFaceAndEntersTheGlove() {
            assertEquals(0, DrinkStream.pointAt(FROM, TO, 0, START, SEED).distanceTo(FROM), DELTA);
            assertEquals(0, DrinkStream.pointAt(FROM, TO, 1, START, SEED).distanceTo(TO), DELTA);
        }

        @Test
        void theStreamSnakesOffTheLineAndWanders() {
            Vec3 straight = FROM.lerp(TO, 0.5);
            Vec3 bent = DrinkStream.pointAt(FROM, TO, 0.5, START, SEED);
            Vec3 later = DrinkStream.pointAt(FROM, TO, 0.5, START + LATER, SEED);

            assertTrue(bent.distanceTo(straight) > OFF_THE_LINE, "the middle leaves the straight line");
            assertTrue(bent.distanceTo(later) > OFF_THE_LINE, "the bend moves with time");
            assertTrue(bent.distanceTo(straight) <= 2 * DrinkStream.SNAKE + DELTA);
        }

        @Test
        void theLiquidSlidesTowardTheGlove() {
            double before = DrinkStream.materialAt(0.5, LENGTH, START);
            double after = DrinkStream.materialAt(0.5, LENGTH, START + 1);

            assertEquals(-LENGTH / DrinkStream.TRAVEL_TICKS, after - before, DELTA);
            assertEquals(LENGTH, DrinkStream.materialAt(1, LENGTH, 0) - DrinkStream.materialAt(0, LENGTH, 0), DELTA);
        }

        @Test
        void theTextureMirrorsEveryBlockWithNoSeam() {
            assertEquals(0, DrinkStream.textureU(0), DELTA);
            assertEquals(1, DrinkStream.textureU(1), DELTA);
            assertEquals(0, DrinkStream.textureU(2), DELTA);
            assertEquals(DrinkStream.textureU(0.999), DrinkStream.textureU(1.001), 1e-2);
        }
    }

    @Nested
    class Shape {

        @Test
        void theRingsRunFromTheTailToTheHeadTenToTheBlock() {
            List<DrinkStream.Ring> rings = DrinkStream.rings(FROM, FROM.add(LENGTH, 0, 0),
                    new DrinkStream.Span(0, 1), START, SEED);

            assertEquals((int) (LENGTH * DrinkStream.RINGS_PER_BLOCK) + 1, rings.size());
            assertEquals(0, rings.getFirst().center().distanceTo(FROM), DELTA);
            assertEquals(0, rings.getLast().center().distanceTo(FROM.add(LENGTH, 0, 0)), DELTA);
            assertTrue(DrinkStream.rings(FROM, TO, new DrinkStream.Span(1, 1), START, SEED).isEmpty());
        }

        @Test
        void theStreamTapersToAPointAtBothEndsAndIsThickBetween() {
            DrinkStream.Span span = new DrinkStream.Span(0, 1);

            assertEquals(0, DrinkStream.radiusAt(0, span, 0, LENGTH, SEED), DELTA);
            assertEquals(0, DrinkStream.radiusAt(1, span, 0, LENGTH, SEED), DELTA);
            assertTrue(DrinkStream.radiusAt(0.5, span, 0, LENGTH, SEED) > DrinkStream.RADIUS * (1 - DrinkStream.SWELL)
                    * (1 - DrinkStream.LUMP) * (1 - DrinkStream.GLOVE_PINCH));
        }

        @Test
        void theStreamPinchesAsItEntersTheGloveAndSwellsAlongTheLiquid() {
            double nearTheBlock = DrinkStream.radiusAt(0.1, UNTAPERED, 0, LENGTH, SEED);
            double nearTheGlove = DrinkStream.radiusAt(0.9, UNTAPERED, 0, LENGTH, SEED);
            double swollen = DrinkStream.radiusAt(0.5, UNTAPERED, DrinkStream.SWELL_SPACING / 4, LENGTH, SEED);
            double pinched = DrinkStream.radiusAt(0.5, UNTAPERED, -DrinkStream.SWELL_SPACING / 4, LENGTH, SEED);

            assertTrue(nearTheGlove < nearTheBlock);
            assertTrue(swollen > pinched);
        }

        @Test
        void aRingsFrameIsRightHandedAboutTheFlow() {
            List<DrinkStream.Ring> rings = DrinkStream.rings(FROM, TO, UNTAPERED, START, SEED);
            DrinkStream.Ring ring = rings.get(rings.size() / 2);
            Vec3 flow = rings.get(rings.size() / 2 + 1).center().subtract(rings.get(rings.size() / 2 - 1).center());

            assertTrue(ring.side().cross(ring.across()).dot(flow) > 0);
            assertEquals(0, ring.side().dot(ring.across()), 1e-6);
            assertEquals(1, ring.outAt(0).length(), 1e-6);
        }
    }
}
