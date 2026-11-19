package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One footprint column of a Build: it walks back from its ground cell, behind
 * the face, down for a footprint on the ground and into the wall for one on a
 * wall, no farther than it rises. The first block with a replaceable cell
 * behind it, or the first block the rising hunk cannot carry, is the anchor:
 * it stays where it is, the blocks in front of it are pushed out along the
 * face's normal by the rise, and stone grows from the anchor's near face. A
 * walk reaching the full rise with no anchor pushes its deepest block from
 * behind, the stone growing in the cells the pushed blocks leave. A walk
 * meeting a block entity fizzles: no column grows and none is charged.
 * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
 * decision column-meeting-a-block-entity-fizzles-at-no-cost
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
     * cannot be replaced, no farther than the rise. A block entity met in the
     * walk, the ground cell included, fizzles the column.
     * decision column-meeting-a-block-entity-fizzles-at-no-cost
     *
     * @param blocks the world the column stands in
     * @param ground the footprint cell
     * @param rise   the rise
     * @param face   the face the footprint lies on
     * @return the column, or empty where the walk meets a block entity
     */
    public static Optional<BuildColumn> reaching(BlockGetter blocks, BlockPos ground, int rise, Direction face) {
        for (int depth = 0; depth < rise; depth++) {
            BlockPos cell = behind(ground, face, depth);
            if (blocks.getBlockState(cell).hasBlockEntity()) {
                return Optional.empty();
            }
            if (anchors(blocks, cell, face)) {
                return Optional.of(new BuildColumn(ground.immutable(), depth, rise, face));
            }
        }
        return Optional.of(new BuildColumn(ground.immutable(), rise, rise, face));
    }

    /**
     * Whether the block in a cell anchors the column: one the rising hunk
     * cannot carry, or one with a cell behind it that stone can replace.
     *
     * @param blocks the world
     * @param cell   the cell the walk stands on
     * @param face   the face the footprint lies on
     * @return true where the block stays and the stone grows from its near face
     */
    private static boolean anchors(BlockGetter blocks, BlockPos cell, Direction face) {
        return !carriable(blocks, cell) || blocks.getBlockState(behind(cell, face, 1)).canBeReplaced();
    }

    /**
     * Whether the rising hunk can carry the block in a cell: one a hand can
     * break, inside the world; bedrock anchors a column.
     *
     * @param blocks the world
     * @param cell   the cell
     * @return true where the hunk can carry its block
     */
    private static boolean carriable(BlockGetter blocks, BlockPos cell) {
        return cell.getY() >= blocks.getMinY() && blocks.getBlockState(cell).getDestroySpeed(blocks, cell) >= 0;
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
