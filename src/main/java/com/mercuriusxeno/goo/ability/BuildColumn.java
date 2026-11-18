package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One footprint column of a Build: it reaches down from its ground cell as
 * many blocks as it rises, stopping short at the first block the rising hunk
 * cannot carry, lifts the blocks it reached by the rise and grows stone in
 * the cells they leave, the stone standing between the lifted blocks and
 * what lies under them. A column stopped short lifts fewer blocks, still by
 * the whole rise on as many blocks of stone as it rises.
 * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
 *
 * @param ground the footprint cell, the column's surface block
 * @param reach  how many blocks, from the ground cell down, the column lifts
 * @param rise   how far the lifted blocks rise, and how many stone blocks grow under them
 */
public record BuildColumn(BlockPos ground, int reach, int rise) {

    /**
     * The column a ground cell reaches at a rise: down from the ground cell
     * while each block can be carried, no farther than the rise.
     *
     * @param ground    the footprint cell
     * @param rise      the rise
     * @param carriable whether the rising hunk can carry the block in a cell
     * @return the column
     */
    public static BuildColumn reaching(BlockPos ground, int rise, Predicate<BlockPos> carriable) {
        int reach = 0;
        while (reach < rise && carriable.test(ground.below(reach))) {
            reach++;
        }
        return new BuildColumn(ground.immutable(), reach, rise);
    }

    /**
     * The cells the column lifts, from the lowest up to the ground cell.
     *
     * @return the cells, bottom up
     */
    public List<BlockPos> liftedCells() {
        List<BlockPos> cells = new ArrayList<>(reach);
        for (int depth = reach - 1; depth >= 0; depth--) {
            cells.add(ground.below(depth));
        }
        return cells;
    }

    /**
     * Where a lifted cell's block stands once the rise ends.
     *
     * @param lifted a cell the column lifts
     * @return the cell the rise carries it to
     */
    public BlockPos liftedTo(BlockPos lifted) {
        return lifted.above(rise);
    }

    /**
     * The cells the column grows stone in: from the lowest lifted cell up, as
     * many as the rise, or from above the ground cell where it lifts nothing.
     *
     * @return the cells, bottom up
     */
    public List<BlockPos> stoneCells() {
        BlockPos lowest = ground.below(reach - 1);
        List<BlockPos> cells = new ArrayList<>(rise);
        for (int height = 0; height < rise; height++) {
            cells.add(lowest.above(height));
        }
        return cells;
    }
}
