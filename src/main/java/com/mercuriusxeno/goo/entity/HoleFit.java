package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a chunk can seat itself on the grid, never overlapping a block and
 * only in the grid column it stands over, so nothing draws it across to a
 * hole more than half a block off. A held chunk pushed into the ground over
 * a hole its shape fits leans into it, so it slots back where it came from
 * when aimed there; a let-go chunk come to rest drops straight down where it
 * stands.
 * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
 */
public final class HoleFit {

    /** How far up or down its column a held chunk pushed into the ground looks for a seat, in cells. */
    public static final int SEARCH_RADIUS = 1;
    /** How far up its column a let-go chunk standing in a block climbs to an open seat at most, in cells. */
    public static final int MOST_CLIMB = 8;
    /** How far down its column a let-go chunk drops to a seat at most, in cells. */
    public static final int MOST_DROP = 64;

    private HoleFit() {
    }

    /**
     * The seat a held chunk pushed into the ground finds in its column: the
     * lowest within {@link #SEARCH_RADIUS} of the aim where every one of its
     * cells stands open and the ground under all its lowest blocks is solid.
     *
     * @param aim   the origin cell the aim pushes the chunk to
     * @param shape the chunk's blocks, each by its offset from the origin cell
     * @param open  whether a cell is open, one the chunk may stand in
     * @return the origin cell of the seat, or empty where none fits
     */
    public static Optional<BlockPos> fitNear(BlockPos aim, List<BlockPos> shape, Predicate<BlockPos> open) {
        for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
            BlockPos origin = aim.above(dy);
            if (fits(origin, shape, open)) {
                return Optional.of(origin);
            }
        }
        return Optional.empty();
    }

    /**
     * Where a let-go chunk come to rest settles, in its own column: dropping
     * through open cells to the first position where at least one of its
     * lowest blocks rests on the ground, so a chunk let go over its hole drops
     * in and one over a ledge settles where it rests; standing in a block, it
     * climbs to the first open such position.
     *
     * @param rest  the origin cell it came to rest in
     * @param shape the chunk's blocks, each by its offset from the origin cell
     * @param open  whether a cell is open, one the chunk may stand in
     * @return the origin cell it settles in, or empty where its column holds none in reach
     */
    public static Optional<BlockPos> settleNear(BlockPos rest, List<BlockPos> shape, Predicate<BlockPos> open) {
        return allOpen(rest, shape, open) ? dropFrom(rest, shape, open) : climbFrom(rest, shape, open);
    }

    private static Optional<BlockPos> climbFrom(BlockPos rest, List<BlockPos> shape, Predicate<BlockPos> open) {
        for (int up = 1; up <= MOST_CLIMB; up++) {
            if (restsAt(rest.above(up), shape, open)) {
                return Optional.of(rest.above(up));
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> dropFrom(BlockPos rest, List<BlockPos> shape, Predicate<BlockPos> open) {
        for (int down = 0; down <= MOST_DROP && allOpen(rest.below(down), shape, open); down++) {
            if (restsAt(rest.below(down), shape, open)) {
                return Optional.of(rest.below(down));
            }
        }
        return Optional.empty();
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

    private static boolean allOpen(BlockPos origin, List<BlockPos> shape, Predicate<BlockPos> open) {
        return shape.stream().allMatch(offset -> open.test(origin.offset(offset)));
    }
}
