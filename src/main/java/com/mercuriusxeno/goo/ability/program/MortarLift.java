package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.entity.BlockHunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Kinetic's Grab over ground painted with Mortar: the grab lifts the ground
 * under the Mortar touching the cell it struck, out of the world as one
 * BlockHunk, each column as deep as its cell's layers. Only Mortar lying on a
 * block's top counts, since the goo's hold Grab reads is downward alone, so
 * the lift is always straight up. Nothing tells of it: a hidden interaction.
 * decision grab-lifts-what-contiguous-rock-goo-paints-as-one-unit
 * decision grab-lift-of-rock-goo-is-always-vertical
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class MortarLift {

    /** The most Mortar cells one lift reads, so a painted field lifts in pieces rather than whole. */
    static final int MOST_CELLS = 256;

    /**
     * The Mortar a cell holds, as the lift reads it.
     *
     * @param layers the cell's layers
     * @param onATop whether the Mortar lies on a block's top
     */
    public record Coat(int layers, boolean onATop) {
    }

    private MortarLift() {
    }

    /**
     * The block columns a grab lifts: under each Mortar cell lying on a top
     * and joined face to face, through such cells, to the grabbed one, the
     * blocks down from the one it lies on, as many as its layers, ending at
     * the first block the lift leaves. A grabbed cell clinging to a wall or a
     * ceiling alone selects nothing.
     *
     * @param grabbed  the Mortar cell grabbed
     * @param coatAt   the Mortar a cell holds, or null for none
     * @param liftable whether the lift takes a ground block
     * @return each Mortar cell's column, top first, in the order the cells join
     */
    public static Map<BlockPos, List<BlockPos>> columns(BlockPos grabbed, Function<BlockPos, @Nullable Coat> coatAt,
                                                        Predicate<BlockPos> liftable) {
        Map<BlockPos, List<BlockPos>> columns = new LinkedHashMap<>();
        Predicate<BlockPos> onATop = at -> {
            Coat coat = coatAt.apply(at);
            return coat != null && coat.onATop();
        };
        for (BlockPos cell : MortarCellBlock.networkFrom(grabbed, onATop, MOST_CELLS)) {
            List<BlockPos> column = columnUnder(cell, coatAt.apply(cell), liftable);
            if (!column.isEmpty()) {
                columns.put(cell, column);
            }
        }
        return columns;
    }

    private static List<BlockPos> columnUnder(BlockPos cell, @Nullable Coat coat, Predicate<BlockPos> liftable) {
        List<BlockPos> column = new ArrayList<>();
        for (int depth = 1; coat != null && depth <= coat.layers() && liftable.test(cell.below(depth)); depth++) {
            column.add(cell.below(depth));
        }
        return List.copyOf(column);
    }

    /**
     * The Mortar cell a look grabs: the cell it struck where that holds
     * Mortar, else the cell above a top it struck.
     *
     * @param hit    the block the look struck
     * @param coatAt the Mortar a cell holds, or null for none
     * @return the grabbed cell, or null where the look struck no Mortar
     */
    public static @Nullable BlockPos grabbedCell(BlockHitResult hit, Function<BlockPos, @Nullable Coat> coatAt) {
        BlockPos struck = hit.getBlockPos();
        if (coatAt.apply(struck) != null) {
            return struck;
        }
        BlockPos above = struck.above();
        return hit.getDirection() == Direction.UP && coatAt.apply(above) != null ? above : null;
    }

    /**
     * Lifts the ground a grab of a Mortar cell selects out of the world as
     * one hunk, its origin the top block under the grabbed cell's patch; the
     * Mortar itself stays where it lay.
     *
     * @param level   the server level
     * @param grabbed the Mortar cell grabbed
     * @return the lifted hunk, or null where the grab selects no block
     */
    public static @Nullable BlockHunk lift(ServerLevel level, BlockPos grabbed) {
        Map<BlockPos, List<BlockPos>> columns = columns(grabbed, cell -> coatIn(level, cell),
                cell -> liftableGround(level, cell));
        List<BlockPos> cells = columns.values().stream().flatMap(List::stream).toList();
        if (cells.isEmpty()) {
            return null;
        }
        BlockPos origin = cells.getFirst();
        List<BlockHunk.HunkBlock> blocks = cells.stream()
                .map(cell -> new BlockHunk.HunkBlock(cell.subtract(origin), level.getBlockState(cell))).toList();
        cells.forEach(cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        BlockHunk hunk = BlockHunk.lift(level, origin, blocks);
        SoundPlays.play(level, hunk.getBoundingBox().getCenter(), GougeStep.TEAR);
        return hunk;
    }

    /**
     * The Mortar a cell of the level holds.
     *
     * @param level the level
     * @param cell  the cell
     * @return its Mortar, or null where it holds none
     */
    static @Nullable Coat coatIn(ServerLevel level, BlockPos cell) {
        int layers = MortarCellBlock.layersAt(level, cell);
        return layers == 0 ? null : new Coat(layers, MortarCellBlock.liesOnATop(level, cell));
    }

    private static boolean liftableGround(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return liftableState(state, state.getDestroySpeed(level, cell));
    }

    /**
     * Whether the lift takes a ground block: one any hand can break, holding
     * no block entity and no fluid.
     *
     * @param state    the block
     * @param hardness its hardness where it stands, negative for one no hand breaks
     * @return true where the lift takes it
     */
    static boolean liftableState(BlockState state, float hardness) {
        return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && hardness >= 0;
    }
}
