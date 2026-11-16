package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * The Molten cells melting the block below them on one server, each by the
 * game tick its heat began: the block stands red hot until the heat has run,
 * then becomes lava.
 * stone-goes-red-hot-before-molten-turns-it-to-lava
 */
public final class MoltenHeats {

    /**
     * A cell in a level.
     *
     * @param dimension the level
     * @param cell      the cell's position
     */
    private record Cell(ResourceKey<Level> dimension, BlockPos cell) {
    }

    private final Map<Cell, Long> heats = new HashMap<>();

    /**
     * @param level the cell's level
     * @param cell  the cell
     * @return the game tick its heat began, empty where it is melting nothing
     */
    public OptionalLong startedAt(Level level, BlockPos cell) {
        Long start = heats.get(new Cell(level.dimension(), cell));
        return start == null ? OptionalLong.empty() : OptionalLong.of(start);
    }

    /**
     * Begins a cell's heat.
     *
     * @param level the cell's level
     * @param cell  the cell
     * @param tick  the game tick it begins
     */
    public void start(Level level, BlockPos cell, long tick) {
        heats.put(new Cell(level.dimension(), cell.immutable()), tick);
    }

    /**
     * Ends a cell's heat.
     *
     * @param level the cell's level
     * @param cell  the cell
     * @return true where the cell was heating
     */
    public boolean end(Level level, BlockPos cell) {
        return heats.remove(new Cell(level.dimension(), cell)) != null;
    }

    /** Drops every heat, as a server stop does. */
    public void clear() {
        heats.clear();
    }
}
