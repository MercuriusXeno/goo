package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a held chunk pushed into the ground can seat itself: the nearest grid
 * position within {@link #SEARCH_RADIUS} of the aim at which every one of the
 * chunk's cells stands open and the ground under its lowest blocks is solid,
 * the hole it was lifted out of being the common case, so the chunk slots
 * back where it came from.
 * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
 */
public final class HoleFit {

    /** How far from the aim, in cells along each axis, a seat is looked for; tuned in playtesting. */
    public static final int SEARCH_RADIUS = 1;

    private HoleFit() {
    }

    /**
     * The nearest seat for a chunk's shape near the aim.
     *
     * @param aim   the origin cell the aim pushes the chunk to
     * @param shape the chunk's blocks, each by its offset from the origin cell
     * @param open  whether a cell is open, one the chunk may stand in
     * @return the origin cell of the nearest seat, or empty where none fits in range
     */
    public static Optional<BlockPos> fitNear(BlockPos aim, List<BlockPos> shape, Predicate<BlockPos> open) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(aim.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                aim.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            candidates.add(cell.immutable());
        }
        candidates.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(aim))
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
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
