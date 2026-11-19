package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The Creep cells a recent walk found free on one server: a charge on any of
 * them can still reach a cell turning its block or a step off the goo. A
 * charge standing on one skips the walk until the verdict lapses, so a charge
 * crossing a wide coat whose way off lies far away pays a whole walk at most
 * once a lapse, not on every cursor update; a coat that has since become
 * stranded is walked again, and its charge ended, once the verdict lapses.
 * sculk-charge-runs-along-host-goo-without-weakening
 */
public final class CreepFreeCells {

    /** The game ticks a free verdict stands before the cells are walked again. */
    static final int FREE_TICKS = 20;

    private final Map<ResourceKey<Level>, Map<BlockPos, Long>> freeUntil = new HashMap<>();

    /**
     * @param level the cell's level
     * @param cell  the cell
     * @param now   the level's game time
     * @return true where a walk found the cell free and the verdict still stands
     */
    public boolean isFree(Level level, BlockPos cell, long now) {
        Long until = freeUntil.getOrDefault(level.dimension(), Map.of()).get(cell);
        return until != null && until > now;
    }

    /**
     * Records the cells a walk found free, dropping every lapsed verdict in the level.
     *
     * @param level the cells' level
     * @param cells the cells the walk reached
     * @param now   the level's game time
     */
    public void markFree(Level level, Collection<BlockPos> cells, long now) {
        Map<BlockPos, Long> inLevel = freeUntil.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        inLevel.values().removeIf(until -> until <= now);
        cells.forEach(cell -> inLevel.put(cell, now + FREE_TICKS));
    }

    /**
     * Drops every verdict, as a server stop does.
     */
    public void clear() {
        freeUntil.clear();
    }
}
