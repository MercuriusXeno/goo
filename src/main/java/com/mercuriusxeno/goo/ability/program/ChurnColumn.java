package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns Churn's column one step: the core rises one layer and the strips
 * sink one, the top core layer feeding the strips' top through
 * {@link ChurnMap} and the strips' bottom feeding the core's bottom back
 * through it. Each core cell and the strip cell it feeds make one loop up
 * the core and down the strip, and a step moves every block one place along
 * its loop. A pinned cell keeps its block and the loop's blocks pass over
 * it, so every block in the column is conserved and none is copied.
 * decision churn-rotates-a-plus-shaped-column
 */
public final class ChurnColumn {

    /** A loop runs up its core column and down its strip column, each one column deep. */
    private static final int COLUMNS_PER_LOOP = 2;

    private ChurnColumn() {
    }

    /**
     * The cells of the column the churn may move a block into or out of.
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
         * @return true where the block stays put and the loop passes over it
         */
        boolean pinned(BlockPos pos);
    }

    /**
     * Moves every block in the column one place along its loop.
     *
     * @param <T>     the block a cell holds
     * @param origin  the north-west core cell of the column's top layer
     * @param depth   the column's layers, the top layer among them
     * @param cells   the column's cells
     */
    public static <T> void turn(BlockPos origin, int depth, Cells<T> cells) {
        for (int coreIndex = 0; coreIndex < ChurnMap.CELLS; coreIndex++) {
            turnLoop(loop(origin, depth, coreIndex), cells);
        }
    }

    /**
     * One core cell's loop: up its core column from the bottom, then down
     * the strip column it feeds from the top.
     *
     * @param origin    the north-west core cell of the top layer
     * @param depth     the column's layers
     * @param coreIndex the core cell
     * @return the loop's cells in the order a block travels them
     */
    static List<BlockPos> loop(BlockPos origin, int depth, int coreIndex) {
        int[] core = ChurnMap.coreCell(coreIndex);
        int[] strip = ChurnMap.stripCellOf(coreIndex);
        List<BlockPos> loop = new ArrayList<>(COLUMNS_PER_LOOP * depth);
        for (int down = depth - 1; down >= 0; down--) {
            loop.add(origin.offset(core[0], -down, core[1]));
        }
        for (int down = 0; down < depth; down++) {
            loop.add(origin.offset(strip[0], -down, strip[1]));
        }
        return loop;
    }

    private static <T> void turnLoop(List<BlockPos> loop, Cells<T> cells) {
        List<BlockPos> moving = loop.stream().filter(pos -> !cells.pinned(pos)).toList();
        if (moving.size() <= 1) {
            return;
        }
        List<T> blocks = moving.stream().map(cells::get).toList();
        for (int i = 0; i < moving.size(); i++) {
            cells.set(moving.get((i + 1) % moving.size()), blocks.get(i));
        }
    }
}
