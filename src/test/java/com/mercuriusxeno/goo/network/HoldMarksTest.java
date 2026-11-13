package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A hold paints each block once with the state standing there, and a block
 * it has stepped leaves the painted set and is never painted again in the
 * hold (decision decay-gnats-degrade-each-block-once).
 */
class HoldMarksTest {

    private static final BlockPos BLOCK = new BlockPos(1, 2, 3);
    private final BlockState stone = mock(BlockState.class);
    private final BlockState cobblestone = mock(BlockState.class);

    @Test
    void aBlockKeepsTheOriginItWasFirstPaintedWith() {
        HoldMarks marks = new HoldMarks();
        marks.paint(BLOCK, stone);
        marks.paint(BLOCK, cobblestone);

        assertEquals(Map.of(BLOCK, stone), marks.painted());
    }

    @Test
    void aSteppedBlockLeavesThePaintedSetAndIsNotPaintedAgain() {
        HoldMarks marks = new HoldMarks();
        marks.paint(BLOCK, stone);
        marks.noteStepped(BLOCK);
        marks.paint(BLOCK, cobblestone);

        assertTrue(marks.painted().isEmpty());
        assertTrue(marks.stepped(BLOCK));
    }

    @Test
    void anUnpaintedBlockCanBePaintedAgain() {
        HoldMarks marks = new HoldMarks();
        marks.paint(BLOCK, stone);
        marks.unpaint(BLOCK);
        marks.paint(BLOCK, cobblestone);

        assertEquals(Map.of(BLOCK, cobblestone), marks.painted());
    }

    /**
     * A cell painted again builds its next step though the hold stepped it,
     * and a cell still building keeps the state it was painted with
     * (decision goop-layers-a-reached-cell-again-while-held).
     */
    @Nested
    class PaintAgain {

        @Test
        void aSteppedBlockPaintedAgainIsPaintedWithItsStateNow() {
            HoldMarks marks = new HoldMarks();
            marks.paint(BLOCK, stone);
            marks.noteStepped(BLOCK);
            marks.paintAgain(BLOCK, cobblestone);

            assertEquals(Map.of(BLOCK, cobblestone), marks.painted());
            assertFalse(marks.stepped(BLOCK));
        }

        @Test
        void aBlockStillPaintedKeepsItsOrigin() {
            HoldMarks marks = new HoldMarks();
            marks.paint(BLOCK, stone);
            marks.paintAgain(BLOCK, cobblestone);

            assertEquals(Map.of(BLOCK, stone), marks.painted());
        }
    }

    /**
     * The swarm's bite waits for its gnats: a mob two blocks off is reached
     * once the gnats have flown two blocks at their speed, and a new mob
     * starts the wait over (decision decay-gnats-degrade-each-block-once).
     */
    @Nested
    class SwarmReach {

        private static final int ZOMBIE = 7;
        private static final int SKELETON = 9;
        private static final double TWO_BLOCKS = 2.0;
        private static final double THREE_AND_A_HALF = 3.5;

        private final HoldMarks marks = new HoldMarks();

        private int flightTicks(double distance) {
            return (int) Math.ceil(distance / SwarmMotes.GNAT_SPEED);
        }

        @Test
        void aMobTheGnatsHaveNotYetReachedIsNotBitten() {
            int first = 1;
            marks.swarmReached(ZOMBIE, first, TWO_BLOCKS);
            assertFalse(marks.swarmReached(ZOMBIE, first + flightTicks(TWO_BLOCKS) - 1, TWO_BLOCKS));
        }

        @Test
        void aMobIsBittenOnceTheGnatsHaveFlownTheGap() {
            int first = 1;
            marks.swarmReached(ZOMBIE, first, TWO_BLOCKS);
            assertTrue(marks.swarmReached(ZOMBIE, first + flightTicks(TWO_BLOCKS), TWO_BLOCKS));
        }

        @Test
        void aNewMobStartsTheWaitOver() {
            int first = 1;
            marks.swarmReached(ZOMBIE, first, TWO_BLOCKS);
            int switched = first + flightTicks(TWO_BLOCKS);
            assertFalse(marks.swarmReached(SKELETON, switched, THREE_AND_A_HALF));
            assertTrue(marks.swarmReached(SKELETON, switched + flightTicks(THREE_AND_A_HALF), THREE_AND_A_HALF));
        }
    }
}
