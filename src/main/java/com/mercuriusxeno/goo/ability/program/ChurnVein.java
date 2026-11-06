package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Churn reaches down the struck block's column for the first mineral, takes
 * the contiguous vein of that block, and lifts it a layer a tick: each vein
 * cell trades places with the ground above it, so the ground flows down
 * through the vein the way a chain of fluid would, until each cell meets the
 * struck block's layer, open air or ground that will not give way. A trade
 * only ever swaps two cells, so every block is conserved and none is copied.
 * decision churn-pulls-a-vein-up-as-a-fluid-chain
 */
public final class ChurnVein {

    private ChurnVein() {
    }

    /**
     * The cells a churn reads and trades.
     *
     * @param <T> the block a cell holds
     */
    public interface Cells<T> {

        /**
         * @param pos a cell
         * @return the block it holds
         */
        T get(BlockPos pos);

        /**
         * @param pos   a cell
         * @param block the block it holds from now
         */
        void set(BlockPos pos, T block);

        /**
         * @param pos a cell
         * @return true where the cell holds a mineral a vein is made of
         */
        boolean mineral(BlockPos pos);

        /**
         * @param pos a cell
         * @return true where the cell's block may be moved at all
         */
        boolean movable(BlockPos pos);

        /**
         * @param pos a cell
         * @return true where the cell is solid ground a vein cell may rise into, carrying nothing that would fall off it
         */
        boolean givesWay(BlockPos pos);
    }

    /**
     * Finds the vein under a struck block: the first mineral down its
     * column within the depth, and every movable cell of that same block
     * joined to it face to face, nearest first, up to the limit.
     *
     * @param <T>    the block a cell holds
     * @param struck the block the churn landed on
     * @param depth  how far down the column the churn reaches, the struck block among it
     * @param limit  the most cells a vein holds
     * @param cells  the level's cells
     * @return the vein's cells, empty where the column holds no mineral
     */
    public static <T> List<BlockPos> find(BlockPos struck, int depth, int limit, Cells<T> cells) {
        for (int down = 0; down < depth; down++) {
            BlockPos seed = struck.below(down);
            if (cells.mineral(seed) && cells.movable(seed)) {
                return flood(seed, limit, cells);
            }
        }
        return List.of();
    }

    private static <T> List<BlockPos> flood(BlockPos seed, int limit, Cells<T> cells) {
        T block = cells.get(seed);
        Set<BlockPos> vein = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        vein.add(seed);
        frontier.add(seed);
        while (!frontier.isEmpty() && vein.size() < limit) {
            spread(frontier.poll(), block, limit, vein, frontier, cells);
        }
        return List.copyOf(vein);
    }

    private static <T> void spread(BlockPos cell, T block, int limit, Set<BlockPos> vein, Deque<BlockPos> frontier,
                                   Cells<T> cells) {
        for (Direction side : Direction.values()) {
            BlockPos next = cell.relative(side);
            if (vein.size() < limit && joins(block, next, cells) && vein.add(next)) {
                frontier.add(next);
            }
        }
    }

    private static <T> boolean joins(T block, BlockPos next, Cells<T> cells) {
        return block.equals(cells.get(next)) && cells.movable(next);
    }

    /**
     * Lifts every vein cell one layer, the highest first, so a stack of vein
     * cells in one column rises together and the ground over it sinks to the
     * stack's foot. A cell stays where the cell above it is the vein's own,
     * stands at the ceiling, or will not give way.
     *
     * @param <T>     the block a cell holds
     * @param vein    the vein's cells
     * @param ceiling the highest layer a vein cell rises to, the struck block's
     * @param cells   the level's cells
     * @return the vein's cells after the lift, in the order given
     */
    public static <T> List<BlockPos> rise(List<BlockPos> vein, int ceiling, Cells<T> cells) {
        Set<BlockPos> standing = new HashSet<>(vein);
        List<BlockPos> risen = new ArrayList<>(vein);
        List<Integer> highestFirst = IntStream.range(0, vein.size()).boxed()
                .sorted(Comparator.comparingInt((Integer index) -> vein.get(index).getY()).reversed()).toList();
        for (int index : highestFirst) {
            BlockPos cell = vein.get(index);
            BlockPos above = cell.above();
            if (cell.getY() < ceiling && !standing.contains(above) && cells.givesWay(above)) {
                T ground = cells.get(above);
                cells.set(above, cells.get(cell));
                cells.set(cell, ground);
                standing.remove(cell);
                standing.add(above);
                risen.set(index, above);
            }
        }
        return risen;
    }
}
