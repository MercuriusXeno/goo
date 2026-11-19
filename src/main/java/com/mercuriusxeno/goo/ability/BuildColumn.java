package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One footprint column of a Build: it walks back from its ground cell, behind
 * the face, down for a footprint on the ground and into the wall for one on a
 * wall, no farther than it rises. The first block with a replaceable cell
 * behind it, or the first block the rising hunk cannot carry, is the anchor:
 * it stays where it is, the blocks in front of it are pushed out along the
 * face's normal by the rise, and stone grows from the anchor's near face. A
 * walk reaching the full rise with no anchor pushes its deepest block from
 * behind, the stone growing in the cells the pushed blocks leave.
 * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
 * decision build-extends-the-first-block-with-air-behind-it
 * decision air-behind-a-block-is-anything-stone-can-replace
 * decision build-pushes-from-behind-at-full-depth-with-no-air
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
     * behind its face, past each block the hunk can carry whose cell behind
     * cannot be replaced, no farther than the rise.
     *
     * @param ground      the footprint cell
     * @param rise        the rise
     * @param face        the face the footprint lies on
     * @param carriable   whether the rising hunk can carry the block in a cell
     * @param replaceable whether stone can replace what fills a cell, the air behind an anchor
     * @return the column
     */
    public static BuildColumn reaching(BlockPos ground, int rise, Direction face, Predicate<BlockPos> carriable,
                                       Predicate<BlockPos> replaceable) {
        int reach = 0;
        while (reach < rise && carriable.test(behind(ground, face, reach))
                && !replaceable.test(behind(ground, face, reach + 1))) {
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
     * The cells the column grows stone in: from the anchor's near face out,
     * as many as the rise, which is the deepest lifted cell, or the cell in
     * front of the ground cell where the ground block is the anchor.
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
