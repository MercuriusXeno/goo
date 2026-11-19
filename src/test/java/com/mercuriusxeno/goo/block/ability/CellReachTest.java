package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what a residual cell reaches, and when: the block under it
 * and the block behind each side it coats, a living thing whose feet stand
 * in it or on it, on the game ticks its cadence falls on.
 * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
 */
class CellReachTest {

    private static final BlockPos CELL = new BlockPos(4, 10, -3);

    @Nested
    class ReachedBlocks {

        @Test
        void aBareCellReachesTheBlockUnderIt() {
            assertEquals(List.of(CELL.below()), CellReach.reachedBlocks(CELL, side -> false));
        }

        @Test
        void aCoatedSideReachesTheBlockBehindIt() {
            Set<Direction> coated = Set.of(Direction.EAST, Direction.UP);

            assertEquals(List.of(CELL.below(), CELL.east(), CELL.above()),
                    CellReach.reachedBlocks(CELL, coated::contains));
        }
    }

    @Nested
    class Standing {

        @Test
        void feetInTheCellStandInIt() {
            assertTrue(CellReach.standsIn(CELL, CELL, CELL.below()));
        }

        @Test
        void feetOnTheCellStandInIt() {
            assertTrue(CellReach.standsIn(CELL, CELL.above(), CELL));
        }

        @Test
        void feetBesideTheCellStandOutsideIt() {
            assertFalse(CellReach.standsIn(CELL, CELL.east(), CELL.east().below()));
        }
    }

    @Nested
    class Cadence {

        private static final int TICKS = 20;

        @Test
        void aTickOnTheCadenceActs() {
            assertTrue(CellReach.actsAt(TICKS * 7L, TICKS));
        }

        @Test
        void aTickBetweenTheCadenceActsNothing() {
            assertFalse(CellReach.actsAt(TICKS * 7L + 3, TICKS));
        }

        @Test
        void theNextActWaitsForTheNextTickOnTheCadence() {
            assertEquals(TICKS - 3, CellReach.ticksToNextAct(TICKS * 7L + 3, TICKS));
        }

        @Test
        void anActOnTheCadenceWaitsAWholeCadenceForTheNext() {
            assertEquals(TICKS, CellReach.ticksToNextAct(TICKS * 7L, TICKS));
        }
    }
}
