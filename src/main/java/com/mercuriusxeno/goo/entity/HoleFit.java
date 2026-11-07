package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a chunk can seat itself on the grid: the lowest, then nearest,
 * position within a radius at which every one of the chunk's cells stands
 * open and the ground under its lowest blocks is solid. A held chunk pushed
 * into the ground looks within {@link #SEARCH_RADIUS} of the aim; a let-go
 * chunk come to rest looks within {@link #SETTLE_RADIUS}, so a peg resting on
 * its hole's rim drops into the hole rather than settling on the rim. The
 * hole it was lifted out of is the common case, so it slots back where it
 * came from, and a chunk never settles where it would overwrite a block.
 * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
 */
public final class HoleFit {

    /** How far from the aim, in cells along each axis, a held chunk looks for a seat; tuned in playtesting. */
    public static final int SEARCH_RADIUS = 1;
    /** How far a let-go chunk come to rest looks for a seat, deep enough to reach its hole from the rim. */
    public static final int SETTLE_RADIUS = 2;

    private HoleFit() {
    }

    /**
     * The seat a held chunk's aim finds, within {@link #SEARCH_RADIUS}.
     *
     * @param aim   the origin cell the aim pushes the chunk to
     * @param shape the chunk's blocks, each by its offset from the origin cell
     * @param open  whether a cell is open, one the chunk may stand in
     * @return the origin cell of the seat, or empty where none fits in range
     */
    public static Optional<BlockPos> fitNear(BlockPos aim, List<BlockPos> shape, Predicate<BlockPos> open) {
        return fitNear(aim, shape, open, SEARCH_RADIUS);
    }

    /**
     * The lowest, then nearest, seat for a chunk's shape within a radius.
     *
     * @param aim    the origin cell to look around
     * @param shape  the chunk's blocks, each by its offset from the origin cell
     * @param open   whether a cell is open, one the chunk may stand in
     * @param radius how far along each axis to look, in cells
     * @return the origin cell of the seat, or empty where none fits in range
     */
    public static Optional<BlockPos> fitNear(BlockPos aim, List<BlockPos> shape, Predicate<BlockPos> open,
                                             int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(aim.offset(-radius, -radius, -radius),
                aim.offset(radius, radius, radius))) {
            candidates.add(cell.immutable());
        }
        candidates.sort(Comparator.comparingInt((BlockPos cell) -> cell.getY()).thenComparingDouble(cell -> cell.distSqr(aim))
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return candidates.stream().filter(origin -> fits(origin, shape, open)).findFirst();
    }

    /**
     * Whether a shape seats at an origin: every cell it fills is open, and the
     * cell under each of its lowest blocks is solid.
     *
     * @param origin the origin cell
     * @param shape  the blocks' offsets from the origin
     * @param open   whether a cell is open
     * @return true where the shape seats
     */
    static boolean fits(BlockPos origin, List<BlockPos> shape, Predicate<BlockPos> open) {
        Set<BlockPos> own = Set.copyOf(shape);
        for (BlockPos offset : shape) {
            if (!open.test(origin.offset(offset))) {
                return false;
            }
            boolean lowest = !own.contains(offset.below());
            if (lowest && open.test(origin.offset(offset.below()))) {
                return false;
            }
        }
        return true;
    }
}
