package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Vacuum count store keeps a cell's count for its memory's ticks after
 * the last tick counted, then holds no entry for it
 * (decision vacuum-breaks-a-cell-the-tick-the-wave-reaches-it).
 */
class VacuumCountsTest {

    private static final ResourceKey<Level> DIMENSION = Level.OVERWORLD;
    private static final BlockPos CELL = new BlockPos(4, 2, 0);
    private static final int MEMORY = 20;
    private static final long COUNTED_AT = 100;

    @Test
    void aCountKeptInsideItsMemoryCarriesOn() {
        VacuumCounts counts = new VacuumCounts();
        counts.countTick(DIMENSION, CELL, COUNTED_AT, MEMORY);

        assertEquals(2, counts.countTick(DIMENSION, CELL, COUNTED_AT + MEMORY, MEMORY));
    }

    @Test
    void aCountPastItsMemoryStartsOver() {
        VacuumCounts counts = new VacuumCounts();
        counts.countTick(DIMENSION, CELL, COUNTED_AT, MEMORY);

        assertEquals(1, counts.countTick(DIMENSION, CELL, COUNTED_AT + MEMORY + 1, MEMORY));
    }

    @Test
    void theStoreHoldsNoEntryOnceTheMemoryHasRun() {
        VacuumCounts counts = new VacuumCounts();
        counts.countTick(DIMENSION, CELL, COUNTED_AT, MEMORY);

        counts.dropForgotten(COUNTED_AT + MEMORY);
        assertTrue(counts.holds(DIMENSION, CELL), "the count is kept through its last remembered tick");
        counts.dropForgotten(COUNTED_AT + MEMORY + 1);

        assertFalse(counts.holds(DIMENSION, CELL));
    }

    @Test
    void theStoreHoldsNoEntryOnceTheCellsLastLayerGoes() {
        VacuumCounts counts = new VacuumCounts();
        counts.countTick(DIMENSION, CELL, COUNTED_AT, MEMORY);

        counts.forget(DIMENSION, CELL);

        assertFalse(counts.holds(DIMENSION, CELL));
    }
}
