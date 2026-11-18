package com.mercuriusxeno.goo.ability.tartarus;

import com.mercuriusxeno.goo.ability.tartarus.TartarusCells.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Where Tartarus sets a player down: it looks up and down the start's
 * column from the start's height, then the columns a few blocks around it,
 * nearest first, for the nearest standing spot, two blocks of air over
 * solid ground that is not lava, within the level's height, so under the
 * nether's bedrock roof. The same cells and start answer the same spot.
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 * A spot with lava or fire beside the player's feet or head, or over their
 * head, is no safe spot either: the nether's lava seas and fires lie beside
 * its floors.
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 */
public final class TartarusPlacement {

    private TartarusPlacement() {
    }

    /**
     * A spot a column answered and how far its feet stand from the start's height.
     *
     * @param feet  the feet of the spot
     * @param climb how many blocks above or below the start's height they stand
     */
    private record Found(BlockPos feet, int climb) {
    }

    /**
     * The nearest safe spot to a start.
     *
     * @param cells  what each block is
     * @param start  where the search starts
     * @param reach  how many blocks around the start's column it looks, each way across X and Z
     * @param bounds the height it looks within
     * @return the feet of the nearest safe spot, empty where none stands in reach
     */
    public static Optional<BlockPos> nearest(TartarusCells cells, BlockPos start, int reach, HeightSpan bounds) {
        int lowest = bounds.bottom() + 1;
        int highest = bounds.top() - 1;
        if (lowest > highest) {
            return Optional.empty();
        }
        int startY = Math.clamp(start.getY(), lowest, highest);
        for (List<BlockPos> ring : ringsAround(start.atY(startY), reach)) {
            Optional<Found> found = ring.stream()
                    .flatMap(column -> nearestInColumn(cells, column, lowest, highest).stream())
                    .min(Comparator.comparingInt(Found::climb));
            if (found.isPresent()) {
                return found.map(Found::feet);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a player stands safe with their feet in a block: two blocks of
     * air on a floor, within the level's height, burning nothing beside them.
     *
     * @param cells  what each block is
     * @param feet   the block their feet fill
     * @param bounds the level's height
     * @return true where they stand safe
     */
    public static boolean standsAt(TartarusCells cells, BlockPos feet, HeightSpan bounds) {
        return feet.getY() > bounds.bottom() && feet.getY() < bounds.top() && standsSafe(cells, feet);
    }

    /**
     * The safe spot nearest the start's height in one column, the block above checked before the block below.
     *
     * @param cells   what each block is
     * @param column  the column, at the start's height
     * @param lowest  the lowest feet the level holds
     * @param highest the highest feet the level holds
     * @return the spot, empty where the column holds none
     */
    private static Optional<Found> nearestInColumn(TartarusCells cells, BlockPos column, int lowest, int highest) {
        int startY = column.getY();
        int farthest = Math.max(startY - lowest, highest - startY);
        for (int climb = 0; climb <= farthest; climb++) {
            int reached = climb;
            Optional<Found> found = safeAt(cells, column.atY(startY + reached), reached, lowest, highest)
                    .or(() -> safeBelow(cells, column, reached, lowest, highest));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Found> safeBelow(TartarusCells cells, BlockPos column, int climb, int lowest,
                                             int highest) {
        return climb == 0 ? Optional.empty() : safeAt(cells, column.below(climb), climb, lowest, highest);
    }

    private static Optional<Found> safeAt(TartarusCells cells, BlockPos feet, int climb, int lowest, int highest) {
        boolean within = feet.getY() >= lowest && feet.getY() <= highest;
        return within && standsSafe(cells, feet) ? Optional.of(new Found(feet, climb)) : Optional.empty();
    }

    private static boolean standsSafe(TartarusCells cells, BlockPos feet) {
        return roomToStand(cells, feet) && !burnsNear(cells, feet);
    }

    private static boolean roomToStand(TartarusCells cells, BlockPos feet) {
        return cells.at(feet) == Cell.OPEN && cells.at(feet.above()) == Cell.OPEN
                && cells.at(feet.below()) == Cell.FLOOR;
    }

    private static boolean burnsNear(TartarusCells cells, BlockPos feet) {
        BlockPos head = feet.above();
        return cells.at(head.above()) == Cell.BURNING || burnsBeside(cells, feet) || burnsBeside(cells, head);
    }

    private static boolean burnsBeside(TartarusCells cells, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (cells.at(pos.relative(side)) == Cell.BURNING) {
                return true;
            }
        }
        return false;
    }

    /**
     * The columns within reach of a start's, grouped by their distance from
     * it, nearest first: the start's own, then each ring around it, each
     * ring's columns in X then Z order.
     *
     * @param start the start's column
     * @param reach how far each way across X and Z the columns reach
     * @return the rings
     */
    private static List<List<BlockPos>> ringsAround(BlockPos start, int reach) {
        NavigableMap<Integer, List<BlockPos>> rings = new TreeMap<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                rings.computeIfAbsent(dx * dx + dz * dz, distance -> new ArrayList<>()).add(start.offset(dx, 0, dz));
            }
        }
        return List.copyOf(rings.values());
    }
}
