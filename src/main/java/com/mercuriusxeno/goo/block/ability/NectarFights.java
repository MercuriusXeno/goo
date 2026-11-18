package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The fights over Nectar cells on one server: the hostiles that reached a
 * cell together and turned on each other, and the game tick their fight
 * ends, when they stop and may try for the goo again.
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 */
public final class NectarFights {

    private final Map<Cell, Fight> fights = new HashMap<>();

    /**
     * A cell in a level.
     *
     * @param dimension the level
     * @param cell      the cell's position
     */
    private record Cell(ResourceKey<Level> dimension, BlockPos cell) {
    }

    /**
     * One fight over a cell.
     *
     * @param endsAt    the game tick the fight ends
     * @param fighterIds the entity ids of the hostiles fighting
     */
    public record Fight(long endsAt, List<Integer> fighterIds) {
    }

    /**
     * @param level the cell's level
     * @param cell  the cell
     * @return the fight over the cell, empty where none stands
     */
    public Optional<Fight> over(Level level, BlockPos cell) {
        return Optional.ofNullable(fights.get(new Cell(level.dimension(), cell)));
    }

    /**
     * Starts a fight over a cell.
     *
     * @param level the cell's level
     * @param cell  the cell
     * @param fight the fight
     */
    public void start(Level level, BlockPos cell, Fight fight) {
        fights.put(new Cell(level.dimension(), cell.immutable()), fight);
    }

    /**
     * Ends the fight over a cell.
     *
     * @param level the cell's level
     * @param cell  the cell
     */
    public void end(Level level, BlockPos cell) {
        fights.remove(new Cell(level.dimension(), cell));
    }

    /** Drops every fight, as a server stop does. */
    public void clear() {
        fights.clear();
    }
}
