package com.mercuriusxeno.goo.ability.yarn;

import java.util.List;
import java.util.Optional;

/**
 * What a slime on Stitch becomes: a mob rolled from the table, drawn only
 * among the entries the cell's layers afford and weighed by each entry's
 * layers, so a costlier mob comes up more often, and the layers the cell
 * keeps once it spends the mob's.
 * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
 *
 * @param mob        the entry rolled
 * @param layersLeft the layers the cell keeps
 */
public record StitchRoll(StitchMob mob, int layersLeft) {

    /**
     * Rolls a mob for a cell.
     *
     * @param cellLayers the cell's layers
     * @param table      the table's entries, in the order the draw walks them
     * @param draw       the random draw, from 0 inclusive to 1 exclusive
     * @return the mob rolled and the layers left, empty where the cell affords no entry
     */
    public static Optional<StitchRoll> roll(int cellLayers, List<StitchMob> table, double draw) {
        List<StitchMob> affordable = table.stream().filter(mob -> mob.layers() <= cellLayers).toList();
        int weight = affordable.stream().mapToInt(StitchMob::layers).sum();
        double landing = draw * weight;
        int reached = 0;
        for (StitchMob mob : affordable) {
            reached += mob.layers();
            if (landing < reached) {
                return Optional.of(new StitchRoll(mob, cellLayers - mob.layers()));
            }
        }
        return Optional.empty();
    }
}
