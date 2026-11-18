package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One footprint column of a Build: it reaches behind its ground cell's face
 * as many blocks as it rises, down for a footprint on the ground and into the
 * wall for one on a wall, stopping short at the first block the rising hunk
 * cannot carry, pushes the blocks it reached out along the face's normal by
 * the rise and grows stone in the cells they leave, the stone standing
 * between the pushed blocks and what lies behind them. A column stopped short
 * pushes fewer blocks, still by the whole rise on as many blocks of stone as
 * it rises.
 * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
 * decision build-goes-sideways
 *
 * @param ground the footprint cell, the column's surface block
 * @param reach  how many blocks, from the ground cell back, the column lifts
 * @param rise   how far the lifted blocks rise, and how many stone blocks grow behind them
 * @param face   the face the footprint lies on, whose normal the column rises along
 */
public record BuildColumn(BlockPos ground, int reach, int rise, Direction face) {

    /**
     * The column a ground cell reaches at a rise: back from the ground cell,
     * behind its face, while each block can be carried, no farther than the rise.
     *
     * @param ground    the footprint cell
     * @param rise      the rise
     * @param face      the face the footprint lies on
     * @param carriable whether the rising hunk can carry the block in a cell
     * @return the column
     */
    public static BuildColumn reaching(BlockPos ground, int rise, Direction face, Predicate<BlockPos> carriable) {
        int reach = 0;
        while (reach < rise && carriable.test(behind(ground, face, reach))) {
            reach++;
        }
        return new BuildColumn(ground.immutable(), reach, rise, face);
    }

    /**
     * The cells the column lifts, from the deepest out to the ground cell.
     *
     * @return the cells, deepest first
     */
    public List<BlockPos> liftedCells() {
        List<BlockPos> cells = new ArrayList<>(reach);
        for (int depth = reach - 1; depth >= 0; depth--) {
            cells.add(behind(ground, face, depth));
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
        return outFrom(lifted, face, rise);
    }

    /**
     * The cells the column grows stone in: from the deepest lifted cell out,
     * as many as the rise, or from in front of the ground cell where it lifts nothing.
     *
     * @return the cells, deepest first
     */
    public List<BlockPos> stoneCells() {
        BlockPos deepest = behind(ground, face, reach - 1);
        List<BlockPos> cells = new ArrayList<>(rise);
        for (int height = 0; height < rise; height++) {
            cells.add(outFrom(deepest, face, height));
        }
        return cells;
    }

    private static BlockPos behind(BlockPos cell, Direction face, int depth) {
        return cell.relative(face.getOpposite(), depth);
    }

    private static BlockPos outFrom(BlockPos cell, Direction face, int distance) {
        return cell.relative(face, distance);
    }
}
