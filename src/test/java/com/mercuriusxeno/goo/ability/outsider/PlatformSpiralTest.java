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
    private static final int COST_STEP = 50;

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
        void eachGrownCellCostsMoreThanTheOneBefore() {
            long before = PlatformSpiral.costOf(PlatformSpiral.START_CELLS, COST_STEP);
            assertTrue(before > 0, "the first grown cell should cost void");
            for (int index = PlatformSpiral.START_CELLS + 1; index < 200; index++) {
                long cost = PlatformSpiral.costOf(index, COST_STEP);

                assertTrue(cost > before, "cell " + index + " should cost more than " + before + ", costs " + cost);
                before = cost;
            }
        }
    }
}
