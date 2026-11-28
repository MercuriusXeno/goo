package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.entity.LeftoverMortar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Kinetic's Grab over blocks painted with Mortar: the grab lifts the blocks
 * behind the Mortar joined to the cell it struck, out of the world as one
 * BlockHunk, each column as deep as its cell's layers, pulled straight out of
 * the face the grabbed Mortar coats. Nothing tells of it: a hidden interaction.
 * decision grab-lifts-what-contiguous-rock-goo-paints-as-one-unit
 * decision grab-lift-of-mortar-pulls-out-of-the-coated-face
 * decision grab-lift-takes-mortar-on-the-grabbed-face-and-plane
 * decision grab-lift-leaves-out-a-column-that-would-get-stuck
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class MortarLift {

    /**
     * The most Mortar cells one lift reads, so a painted field lifts in pieces rather than whole.
     * decision grab-lift-takes-mortar-on-the-grabbed-face-and-plane
     */
    static final int MOST_CELLS = 256;
    /** The sides Mortar clings to beside the top it lies on: the four walls and the ceiling. */
    private static final List<Direction> CLINGING = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST, Direction.UP);

    /**
     * The Mortar a cell holds, as the lift reads it.
     *
     * @param layers the cell's layers
     * @param coated each side of the cell whose neighbour the Mortar coats: down for a top it lies on
     */
    public record Coat(int layers, Set<Direction> coated) {
    }

    /**
     * The Mortar cell a grab takes and the side of it whose neighbour's face the grab pulls out of.
     *
     * @param cell   the grabbed Mortar cell
     * @param coated the side of the cell holding the coated block
     */
    public record Grabbed(BlockPos cell, Direction coated) {

        /**
         * The way the lifted piece leaves its hole, straight out of the coated face.
         *
         * @return the pull
         */
        public Direction pull() {
            return coated.getOpposite();
        }
    }

    private MortarLift() {
    }

    /**
     * The block columns a grab lifts: behind each Mortar cell coating the
     * grabbed face, in the grabbed cell's plane and joined face to face,
     * through such cells, to the grabbed one, the blocks running away from
     * the pull from the one it coats, as many as its layers, ending at the
     * first block the lift leaves. A column whose way out along the pull is
     * blocked within the distance the piece travels to clear its hole is
     * left out whole.
     *
     * @param grabbed  the Mortar cell grabbed and the side it coats
     * @param coatAt   the Mortar a cell holds, or null for none
     * @param liftable whether the lift takes a block
     * @param blocking whether a block stands in a lifted piece's way
     * @return each Mortar cell's column, nearest the face first, in the order the cells join
     */
    public static Map<BlockPos, List<BlockPos>> columns(Grabbed grabbed, Function<BlockPos, @Nullable Coat> coatAt,
                                                        Predicate<BlockPos> liftable, Predicate<BlockPos> blocking) {
        Direction coated = grabbed.coated();
        Direction.Axis axis = coated.getAxis();
        int plane = grabbed.cell().get(axis);
        Predicate<BlockPos> onTheGrabbedFace = at -> {
            Coat coat = coatAt.apply(at);
            return coat != null && coat.coated().contains(coated) && at.get(axis) == plane;
        };
        Map<BlockPos, List<BlockPos>> columns = new LinkedHashMap<>();
        for (BlockPos cell : MortarCellBlock.networkFrom(grabbed.cell(), onTheGrabbedFace, MOST_CELLS)) {
            List<BlockPos> column = columnBehind(cell, coated, coatAt.apply(cell), liftable);
            if (!column.isEmpty()) {
                columns.put(cell, column);
            }
        }
        return leaveOutTheStuck(columns, grabbed.pull(), coatAt, blocking);
    }

    private static List<BlockPos> columnBehind(BlockPos cell, Direction coated, @Nullable Coat coat,
                                               Predicate<BlockPos> liftable) {
        List<BlockPos> column = new ArrayList<>();
        for (int depth = 1; coat != null && depth <= coat.layers() && liftable.test(cell.relative(coated, depth));
             depth++) {
            column.add(cell.relative(coated, depth));
        }
        return List.copyOf(column);
    }

    /**
     * The columns left once each whose way out is blocked is dropped,
     * dropped again until none drops, since dropping the deepest shortens the
     * distance the piece travels.
     * decision grab-lift-leaves-out-a-column-that-would-get-stuck
     *
     * @param columns  each Mortar cell's column
     * @param pull     the way the piece leaves its hole
     * @param coatAt   the Mortar a cell holds, or null for none
     * @param blocking whether a block stands in a lifted piece's way
     * @return the columns kept
     */
    private static Map<BlockPos, List<BlockPos>> leaveOutTheStuck(Map<BlockPos, List<BlockPos>> columns,
                                                                  Direction pull,
                                                                  Function<BlockPos, @Nullable Coat> coatAt,
                                                                  Predicate<BlockPos> blocking) {
        Map<BlockPos, List<BlockPos>> kept = new LinkedHashMap<>(columns);
        boolean dropped = true;
        while (dropped) {
            int travel = kept.values().stream().mapToInt(List::size).max().orElse(0);
            dropped = kept.keySet().removeIf(cell -> wayOutBlocked(cell, pull, travel, coatAt, blocking));
        }
        return Collections.unmodifiableMap(kept);
    }

    /**
     * Whether a column's way out is blocked: a block standing in the shaft
     * its piece sweeps, from its Mortar cell along the pull for the distance
     * the piece travels, that is no Mortar and stands in the way.
     *
     * @param cell     the column's Mortar cell
     * @param pull     the way the piece leaves its hole
     * @param travel   how far the piece travels to clear its hole, the deepest column kept
     * @param coatAt   the Mortar a cell holds, or null for none
     * @param blocking whether a block stands in a lifted piece's way
     * @return true where a block stands in the shaft
     */
    private static boolean wayOutBlocked(BlockPos cell, Direction pull, int travel,
                                         Function<BlockPos, @Nullable Coat> coatAt, Predicate<BlockPos> blocking) {
        for (int step = 0; step < travel; step++) {
            BlockPos shaft = cell.relative(pull, step);
            if (coatAt.apply(shaft) == null && blocking.test(shaft)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The Mortar cell a look grabs and the face it pulls out of: the struck
     * cell where that holds Mortar, coated on the side opposite the face the
     * look struck where it coats it, else on the top it lies on; elsewhere the
     * cell beside the struck face, where its Mortar coats that face.
     *
     * @param hit    the block the look struck
     * @param coatAt the Mortar a cell holds, or null for none
     * @return the grabbed cell and the side it coats, or null where the look struck no Mortar coating a face
     */
    public static @Nullable Grabbed grabbedCell(BlockHitResult hit, Function<BlockPos, @Nullable Coat> coatAt) {
        BlockPos struck = hit.getBlockPos();
        Coat struckCoat = coatAt.apply(struck);
        return struckCoat != null ? struckMortar(struck, hit.getDirection().getOpposite(), struckCoat)
                : mortarOnTheFace(struck.relative(hit.getDirection()), hit.getDirection().getOpposite(), coatAt);
    }

    private static @Nullable Grabbed struckMortar(BlockPos struck, Direction behind, Coat coat) {
        if (coat.coated().contains(behind)) {
            return new Grabbed(struck, behind);
        }
        return coat.coated().contains(Direction.DOWN) ? new Grabbed(struck, Direction.DOWN) : null;
    }

    private static @Nullable Grabbed mortarOnTheFace(BlockPos beside, Direction toTheFace,
                                                     Function<BlockPos, @Nullable Coat> coatAt) {
        Coat coat = coatAt.apply(beside);
        return coat != null && coat.coated().contains(toTheFace) ? new Grabbed(beside, toTheFace) : null;
    }

    /**
     * Lifts the blocks a grab of a Mortar cell selects out of the world as
     * one hunk, its origin the block behind the grabbed cell's patch nearest
     * the face, each lifted column's Mortar paying one layer for each block
     * it lifted; what Mortar is left leaves its cell to ride the piece.
     * decision mortar-lift-spends-one-layer-per-block-lifted
     * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
     *
     * @param level   the server level
     * @param grabbed the Mortar cell grabbed and the side it coats
     * @return the lifted hunk, or null where the grab selects no block
     */
    public static @Nullable BlockHunk lift(ServerLevel level, Grabbed grabbed) {
        Map<BlockPos, List<BlockPos>> columns = columns(grabbed, cell -> coatIn(level, cell),
                cell -> liftableGround(level, cell), cell -> !level.getBlockState(cell).canBeReplaced());
        List<BlockPos> cells = columns.values().stream().flatMap(List::stream).toList();
        if (cells.isEmpty()) {
            return null;
        }
        BlockPos origin = cells.getFirst();
        List<BlockHunk.HunkBlock> blocks = cells.stream()
                .map(cell -> new BlockHunk.HunkBlock(cell.subtract(origin), level.getBlockState(cell))).toList();
        List<LeftoverMortar.Riding> riding = new ArrayList<>();
        columns.forEach((cell, column) -> leftoverOf(level, cell, column, origin, grabbed.pull())
                .ifPresent(riding::add));
        columns.keySet().forEach(cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        cells.forEach(cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        BlockHunk hunk = BlockHunk.lift(level, origin, blocks, riding);
        SoundPlays.play(level, hunk.getBoundingBox().getCenter(), GougeStep.TEAR);
        return hunk;
    }

    /**
     * What a lifted column's Mortar leaves once it pays a layer for each
     * block its column lifted: the layers left, riding the column's block
     * nearest the face on the face it coated.
     * decision mortar-lift-spends-one-layer-per-block-lifted
     * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
     *
     * @param level  the server level
     * @param cell   the column's Mortar cell
     * @param column the blocks its column lifted, nearest the face first
     * @param origin the hunk's origin cell
     * @param pull   the way the piece leaves its hole, the face the Mortar coats
     * @return the riding Mortar, or none where the cost took every layer
     */
    private static Optional<LeftoverMortar.Riding> leftoverOf(ServerLevel level, BlockPos cell, List<BlockPos> column,
                                                             BlockPos origin, Direction pull) {
        int left = MortarCellBlock.layersAt(level, cell) - column.size();
        return left > 0 ? Optional.of(new LeftoverMortar.Riding(column.getFirst().subtract(origin), pull, left))
                : Optional.empty();
    }

    /**
     * The Mortar a cell of the level holds.
     *
     * @param level the level
     * @param cell  the cell
     * @return its Mortar, or null where it holds none
     */
    static @Nullable Coat coatIn(BlockGetter level, BlockPos cell) {
        int layers = MortarCellBlock.layersAt(level, cell);
        return layers == 0 ? null : new Coat(layers, coatedSides(level, cell));
    }

    private static Set<Direction> coatedSides(BlockGetter level, BlockPos cell) {
        Set<Direction> coated = EnumSet.noneOf(Direction.class);
        if (MortarCellBlock.liesOnATop(level, cell)) {
            coated.add(Direction.DOWN);
        }
        BlockState state = level.getBlockState(cell);
        CLINGING.stream().filter(side -> VolumeCellBlock.coatOn(state, side) == SideCoat.ATTACHED)
                .forEach(coated::add);
        return Set.copyOf(coated);
    }

    private static boolean liftableGround(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return liftableState(state, state.getDestroySpeed(level, cell));
    }

    /**
     * Whether the lift takes a block: one any hand can break, holding no
     * block entity and no fluid.
     *
     * @param state    the block
     * @param hardness its hardness where it stands, negative for one no hand breaks
     * @return true where the lift takes it
     */
    static boolean liftableState(BlockState state, float hardness) {
        return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && hardness >= 0;
    }
}
