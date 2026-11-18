package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;

/**
 * The ticks Vacuum has counted on each goo cell toward its next layer on one
 * server, kept past the hold for the memory the step names after the last
 * tick the cone counted on the cell, then dropped.
 * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
 */
public final class VacuumCounts {

    /**
     * A cell in a level.
     *
     * @param dimension the level
     * @param pos       the cell's position
     */
    private record Cell(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /**
     * A cell's count.
     *
     * @param ticks       the ticks counted toward its next layer
     * @param forgetAfter the last server tick the count is kept through
     */
    private record Count(int ticks, long forgetAfter) {
    }

    private final Map<Cell, Count> counts = new HashMap<>();

    /**
     * Counts one tick the cone reaches a cell, on top of the count the cell
     * kept, or from zero where it kept none or its memory ran out.
     *
     * @param dimension   the cell's level
     * @param pos         the cell
     * @param tick        the server tick counted
     * @param memoryTicks the ticks the count is kept after this one
     * @return the cell's count after this tick
     */
    public int countTick(ResourceKey<Level> dimension, BlockPos pos, long tick, int memoryTicks) {
        Cell cell = new Cell(dimension, pos.immutable());
        Count kept = counts.get(cell);
        int ticks = (kept == null || tick > kept.forgetAfter() ? 0 : kept.ticks()) + 1;
        counts.put(cell, new Count(ticks, tick + memoryTicks));
        return ticks;
    }

    /**
     * Drops a cell's count, as a layer taken or a cell emptied does.
     *
     * @param dimension the cell's level
     * @param pos       the cell
     */
    public void forget(ResourceKey<Level> dimension, BlockPos pos) {
        counts.remove(new Cell(dimension, pos));
    }

    /**
     * Drops every count whose memory has run by a server tick.
     *
     * @param tick the server tick
     */
    public void dropForgotten(long tick) {
        counts.values().removeIf(count -> tick > count.forgetAfter());
    }

    /**
     * @param dimension the cell's level
     * @param pos       the cell
     * @return true where the store holds a count for the cell
     */
    public boolean holds(ResourceKey<Level> dimension, BlockPos pos) {
        return counts.containsKey(new Cell(dimension, pos));
    }

    /** Drops every count, as a server stop does. */
    public void clear() {
        counts.clear();
    }
}
