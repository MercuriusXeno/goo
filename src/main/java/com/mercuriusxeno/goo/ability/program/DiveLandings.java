package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Picks where a dive lands: a random cell the diver can stand in within a
 * sphere around where it stands, a cell below its feet weighing
 * {@link #BELOW_WEIGHT} times a cell level with or above them.
 * decision dive-drops-you-to-a-cave-below
 */
public final class DiveLandings {

    /** How many times a cell below the diver's feet outweighs one level with or above them. */
    static final int BELOW_WEIGHT = 4;

    private DiveLandings() {
    }

    /**
     * Rolls a landing among the standable cells within the radius.
     *
     * @param feet      the cell the diver's feet stand in
     * @param radius    how far from the feet a landing may lie, in blocks
     * @param standable whether the diver can stand with its feet in a cell
     * @param random    the roll's source
     * @return the landing cell, empty where no cell in reach is standable
     */
    public static Optional<BlockPos> pick(BlockPos feet, int radius, Predicate<BlockPos> standable,
            RandomSource random) {
        List<BlockPos> below = new ArrayList<>();
        List<BlockPos> levelOrAbove = new ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    sortCell(feet, feet.offset(dx, dy, dz), radius, standable, dy < 0 ? below : levelOrAbove);
                }
            }
        }
        return roll(below, levelOrAbove, random);
    }

    private static void sortCell(BlockPos feet, BlockPos cell, int radius, Predicate<BlockPos> standable,
            List<BlockPos> landings) {
        if (cell.distSqr(feet) <= (double) radius * radius && !cell.equals(feet) && standable.test(cell)) {
            landings.add(cell);
        }
    }

    private static Optional<BlockPos> roll(List<BlockPos> below, List<BlockPos> levelOrAbove, RandomSource random) {
        int belowShare = below.size() * BELOW_WEIGHT;
        int total = belowShare + levelOrAbove.size();
        if (total == 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        return Optional.of(roll < belowShare ? below.get(roll / BELOW_WEIGHT) : levelOrAbove.get(roll - belowShare));
    }
}
