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
 * Covers what an Infest cell gnaws and bites, and when: the block under it
 * and the block behind each side it coats, a living thing whose feet stand
 * in it or on it, on the game ticks its cadence falls on.
 * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
 */
class InfestGnawTest {

    private static final BlockPos CELL = new BlockPos(4, 10, -3);

    @Nested
    class GnawedBlocks {

        @Test
        void aBareCellGnawsTheBlockUnderIt() {
            assertEquals(List.of(CELL.below()), InfestGnaw.gnawedBlocks(CELL, side -> false));
        }

        @Test
        void aCoatedSideGnawsTheBlockBehindIt() {
            Set<Direction> coated = Set.of(Direction.EAST, Direction.UP);

            assertEquals(List.of(CELL.below(), CELL.east(), CELL.above()),
                    InfestGnaw.gnawedBlocks(CELL, coated::contains));
        }
    }

    @Nested
    class Standing {

        @Test
        void feetInTheCellStandInIt() {
            assertTrue(InfestGnaw.standsIn(CELL, CELL, CELL.below()));
        }

        @Test
        void feetOnTheCellStandInIt() {
            assertTrue(InfestGnaw.standsIn(CELL, CELL.above(), CELL));
        }

        @Test
        void feetBesideTheCellStandOutsideIt() {
            assertFalse(InfestGnaw.standsIn(CELL, CELL.east(), CELL.east().below()));
        }
    }

    @Nested
    class Cadence {

        private static final int TICKS = 20;

        @Test
        void aTickOnTheCadenceGnaws() {
            assertTrue(InfestGnaw.gnawsAt(TICKS * 7L, TICKS));
        }

        @Test
        void aTickBetweenTheCadenceGnawsNothing() {
            assertFalse(InfestGnaw.gnawsAt(TICKS * 7L + 3, TICKS));
        }

        @Test
        void theNextGnawWaitsForTheNextTickOnTheCadence() {
            assertEquals(TICKS - 3, InfestGnaw.ticksToNextGnaw(TICKS * 7L + 3, TICKS));
        }

        @Test
        void aGnawOnTheCadenceWaitsAWholeCadenceForTheNext() {
            assertEquals(TICKS, InfestGnaw.ticksToNextGnaw(TICKS * 7L, TICKS));
        }
    }
}
