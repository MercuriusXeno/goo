package com.mercuriusxeno.goo.ability.outsider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the order Outsider's platform grows in and what each cell costs. */
class PlatformSpiralTest {

    private static final int SEVEN_BY_SEVEN = 49;
    private static final int CELL_BASE = 10;
    private static final int PER_DISTANCE = 1;

    @Nested
    class Order {

        @Test
        void startingCellsAreTheVoidstoneAndItsRing() {
            Set<PlatformSpiral.Cell> start = new HashSet<>();
            for (int index = 0; index < PlatformSpiral.START_CELLS; index++) {
                start.add(PlatformSpiral.cellAt(index));
            }
            Set<PlatformSpiral.Cell> ring = new HashSet<>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ring.add(new PlatformSpiral.Cell(x, z));
                }
            }

            assertEquals(ring, start);
            assertEquals(new PlatformSpiral.Cell(0, 0), PlatformSpiral.cellAt(0));
        }

        @Test
        void eachCellSharesAnEdgeWithTheOneBeforeAndNoneRepeats() {
            Set<PlatformSpiral.Cell> grown = new HashSet<>();
            PlatformSpiral.Cell before = PlatformSpiral.cellAt(0);
            grown.add(before);
            for (int index = 1; index < SEVEN_BY_SEVEN; index++) {
                PlatformSpiral.Cell cell = PlatformSpiral.cellAt(index);
                int step = Math.abs(cell.x() - before.x()) + Math.abs(cell.z() - before.z());

                assertEquals(1, step, "cell " + index + " at " + cell + " should touch " + before);
                assertTrue(grown.add(cell), "cell " + index + " at " + cell + " should be new");
                assertTrue(Math.abs(cell.x()) <= 3 && Math.abs(cell.z()) <= 3,
                        "cell " + index + " at " + cell + " should stand within the seven by seven square");
                before = cell;
            }
        }
    }

    @Nested
    class Cost {

        @Test
        void aCellCostsTheBasePlusOnePerBlockOfManhattanDistance() {
            for (int index = PlatformSpiral.START_CELLS; index < 200; index++) {
                PlatformSpiral.Cell cell = PlatformSpiral.cellAt(index);
                long distance = Math.abs(cell.x()) + Math.abs(cell.z());

                assertEquals(CELL_BASE + distance, PlatformSpiral.costOf(index, CELL_BASE, PER_DISTANCE),
                        "cell " + index + " at " + cell + " should cost " + CELL_BASE + " plus " + distance);
            }
        }

        @Test
        void theFirstGrownCellThreeBlocksOutCostsThirteen() {
            assertEquals(13, PlatformSpiral.costOf(PlatformSpiral.START_CELLS, CELL_BASE, PER_DISTANCE));
        }
    }

    @Nested
    class Pace {

        private static final int RING_TICKS = 40;

        @Test
        void eachCellSitsInTheRingItsSquareGrows() {
            assertEquals(0, PlatformSpiral.ringOf(0));
            assertEquals(1, PlatformSpiral.ringOf(1));
            assertEquals(1, PlatformSpiral.ringOf(8));
            assertEquals(2, PlatformSpiral.ringOf(9));
            assertEquals(2, PlatformSpiral.ringOf(24));
            assertEquals(3, PlatformSpiral.ringOf(25));
        }

        @Test
        void theFeedFinishesEveryRingInTheRingTime() {
            for (int ring = 2; ring <= 40; ring++) {
                int first = (2 * ring - 1) * (2 * ring - 1);
                long ringCost = 0;
                for (int index = first; index < (2 * ring + 1) * (2 * ring + 1); index++) {
                    ringCost += PlatformSpiral.costOf(index, CELL_BASE, PER_DISTANCE);
                }
                long feed = PlatformSpiral.feedFor(first, CELL_BASE, PER_DISTANCE, RING_TICKS);

                assertTrue(feed * RING_TICKS >= ringCost && (feed - 1) * RING_TICKS < ringCost,
                        "ring " + ring + " costs " + ringCost + ", fed " + feed + " a tick for " + RING_TICKS);
            }
        }
    }
}
