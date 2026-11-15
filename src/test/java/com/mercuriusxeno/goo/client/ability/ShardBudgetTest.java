package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many of a Blast's broken cells throw shards and how many each throws:
 * the smallest Blast throws from every cell, and the largest stays under the
 * budget, which sits below the client's particle cap. Cell counts are the
 * cube around each reach, more than its sphere can break.
 */
class ShardBudgetTest {

    private static final int SMALLEST_BLAST = 2;
    private static final int SIZE_SEVEN_BLAST = 7;
    private static final int LARGEST_BLAST = 9;

    private static int cubeCells(int reach) {
        int side = 2 * reach + 1;
        return side * side * side;
    }

    // blast-broken-blocks-throw-their-own-break-shards
    @Test
    void everyCellOfTheSmallestBlastThrowsShards() {
        ShardBudget budget = ShardBudget.forCells(cubeCells(SMALLEST_BLAST));

        assertEquals(1, budget.everyNthCell());
        assertTrue(budget.shardsPerCell() >= 1, "each cell throws " + budget.shardsPerCell());
    }

    @ParameterizedTest
    @ValueSource(ints = {SIZE_SEVEN_BLAST, LARGEST_BLAST})
    void aLargeBlastsShardsTotalUnderTheBudget(int size) {
        int cells = cubeCells(size);
        ShardBudget budget = ShardBudget.forCells(cells);

        assertTrue(budget.total(cells) < ShardBudget.TOTAL_SHARDS, "size " + size + " total " + budget.total(cells));
        assertTrue(ShardBudget.TOTAL_SHARDS < ShardBudget.CLIENT_PARTICLE_CAP);
    }

    @Test
    void cellsPastTheBudgetThrowFromEveryNthCellAlone() {
        int cells = ShardBudget.TOTAL_SHARDS * 3 + 1;
        ShardBudget budget = ShardBudget.forCells(cells);

        assertEquals(4, budget.everyNthCell());
        assertTrue(budget.total(cells) <= ShardBudget.TOTAL_SHARDS, "total " + budget.total(cells));
    }

    @Test
    void aFewCellsThrowAFullBreakEach() {
        assertEquals(ShardBudget.FULL_BREAK_SHARDS, ShardBudget.forCells(1).shardsPerCell());
    }
}
