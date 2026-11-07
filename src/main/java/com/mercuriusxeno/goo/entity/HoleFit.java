package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a chunk can seat itself on the grid, never overlapping a block. A
 * held chunk pushed into the ground leans toward the lowest, then nearest,
 * position within {@link #SEARCH_RADIUS} of the aim where every one of its
 * cells stands open and the ground under all its lowest blocks is solid, the
 * hole it was lifted out of being the common case, so it slots back where it
 * came from. A let-go chunk come to rest settles where its cells are open and
 * it rests on the ground (settleNear).
 * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
 */
public final class HoleFit {

    /** How far from the aim, in cells along each axis, a held chunk looks for a seat; tuned in playtesting. */
    public static final int SEARCH_RADIUS = 1;
    /** How far a let-go chunk come to rest looks for a seat, deep enough to reach its hole from the rim. */
    public static final int SETTLE_RADIUS = 2;
    /** How far a let-go chunk looks for an open seat at most, where none stands within {@link #SETTLE_RADIUS}. */
    public static final int MOST_SETTLE_RADIUS = 8;

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
        return candidates(aim, radius, lowestThenNearest(aim)).stream()
                .filter(origin -> fits(origin, shape, open)).findFirst();
    }

    /**
     * Where a let-go chunk come to rest settles: the lowest, then nearest,
     * position within {@link #SETTLE_RADIUS} where every one of its cells is
     * open and at least one of its lowest blocks rests on the ground, so a peg
     * on its hole's rim drops in and a chunk over a ledge settles where it
     * rests; where none stands that near, the nearest such position further
     * out, up to {@link #MOST_SETTLE_RADIUS}, so it never overlaps a block.
     *
     * @param rest  the origin cell it came to rest in
     * @param shape the chunk's blocks, each by its offset from the origin cell
     * @param open  whether a cell is open, one the chunk may stand in
     * @return the origin cell it settles in, or empty where nothing in reach is open
     */
    public static Optional<BlockPos> settleNear(BlockPos rest, List<BlockPos> shape, Predicate<BlockPos> open) {
        Optional<BlockPos> near = candidates(rest, SETTLE_RADIUS, lowestThenNearest(rest)).stream()
                .filter(origin -> restsAt(origin, shape, open)).findFirst();
        if (near.isPresent()) {
            return near;
        }
        Comparator<BlockPos> nearest = Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(rest));
        return candidates(rest, MOST_SETTLE_RADIUS, nearest.thenComparingInt(BlockPos::getY)).stream()
                .filter(origin -> restsAt(origin, shape, open)).findFirst();
    }

    private static Comparator<BlockPos> lowestThenNearest(BlockPos around) {
        return Comparator.comparingInt((BlockPos cell) -> cell.getY()).thenComparingDouble(cell -> cell.distSqr(around));
    }

    private static List<BlockPos> candidates(BlockPos around, int radius, Comparator<BlockPos> order) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(around.offset(-radius, -radius, -radius),
                around.offset(radius, radius, radius))) {
            candidates.add(cell.immutable());
        }
        candidates.sort(order.thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return candidates;
    }

    /**
     * Whether a shape rests at an origin: every cell it fills is open, and at
     * least one of its lowest blocks stands on a cell that is not.
     *
     * @param origin the origin cell
     * @param shape  the blocks' offsets from the origin
     * @param open   whether a cell is open
     * @return true where the shape rests
     */
    static boolean restsAt(BlockPos origin, List<BlockPos> shape, Predicate<BlockPos> open) {
        Set<BlockPos> own = Set.copyOf(shape);
        boolean resting = false;
        for (BlockPos offset : shape) {
            if (!open.test(origin.offset(offset))) {
                return false;
            }
            resting |= !own.contains(offset.below()) && !open.test(origin.offset(offset.below()));
        }
        return resting;
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
