package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * The blocks Molten cells are heating on one server, each by the cell
 * heating it and the game tick its heat began: the block stands red hot
 * until the heat has run, then melts or cooks. A cell runs one heat for each
 * block it touches.
 * stone-goes-red-hot-before-molten-turns-it-to-lava
 * molten-heats-every-block-its-cell-touches
 */
public final class MoltenHeats {

    /**
     * A block a cell heats, in a level.
     *
     * @param dimension the level
     * @param cell      the heating cell's position
     * @param heated    the heated block's position
     */
    private record Heat(ResourceKey<Level> dimension, BlockPos cell, BlockPos heated) {
    }

    private final Map<Heat, Long> heats = new HashMap<>();

    /**
     * @param level  the cell's level
     * @param cell   the cell
     * @param heated the block it heats
     * @return the game tick the cell's heat on that block began, empty where it heats it not
     */
    public OptionalLong startedAt(Level level, BlockPos cell, BlockPos heated) {
        Long start = heats.get(new Heat(level.dimension(), cell, heated));
        return start == null ? OptionalLong.empty() : OptionalLong.of(start);
    }

    /**
     * Begins a cell's heat on a block.
     *
     * @param level  the cell's level
     * @param cell   the cell
     * @param heated the block it heats
     * @param tick   the game tick it begins
     */
    public void start(Level level, BlockPos cell, BlockPos heated, long tick) {
        heats.put(new Heat(level.dimension(), cell.immutable(), heated.immutable()), tick);
    }

    /**
     * Ends a cell's heat on a block.
     *
     * @param level  the cell's level
     * @param cell   the cell
     * @param heated the block it heats
     * @return true where the cell was heating it
     */
    public boolean end(Level level, BlockPos cell, BlockPos heated) {
        return heats.remove(new Heat(level.dimension(), cell, heated)) != null;
    }

    /**
     * The blocks a cell is heating.
     *
     * @param level the cell's level
     * @param cell  the cell
     * @return each block it heats
     */
    public List<BlockPos> heatedBy(Level level, BlockPos cell) {
        return heats.keySet().stream().filter(heat -> heat.dimension() == level.dimension()
                && heat.cell().equals(cell)).map(Heat::heated).toList();
    }

    /** Drops every heat, as a server stop does. */
    public void clear() {
        heats.clear();
    }
}
