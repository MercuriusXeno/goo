package com.mercuriusxeno.goo.ability.outsider;

/**
 * A square spiral out from a center, cell 0, and what each of its rings
 * costs. Ring k holds the 8k cells k blocks out, walked south down the east
 * side, west along the south side, north up the west side and east along
 * the north side, so each cell shares an edge with the one before and the
 * 3 by 3 around the center is cells 0 to 8. {@link RoomGrid} lays the rooms
 * along it. A cell costs a base plus a little for each block of Manhattan
 * distance from the center, as the operator ruled, and a room's
 * {@link RoomExtent} pays a ring's whole cost to grow through it.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision outsider-room-grows-fractionally-on-all-sides-at-once
 */
public final class PlatformSpiral {

    /** The cells a room starts with: the voidstone and its ring. */
    public static final int START_CELLS = 9;
    private static final int SIDES = 4;
    private static final int EAST_SIDE = 0;
    private static final int SOUTH_SIDE = 1;
    private static final int WEST_SIDE = 2;
    /** A ring k out runs 2k cells along each side, so its square is 2k + 1 cells across. */
    private static final int PER_RING = 2;

    private PlatformSpiral() {
    }

    /**
     * A cell's offset from the voidstone along the floor.
     *
     * @param x blocks east
     * @param z blocks south
     */
    public record Cell(int x, int z) {
    }

    /**
     * The cell a platform of some size grows next.
     *
     * @param index the platform's cell count, the voidstone being cell 0
     * @return the cell's offset from the voidstone
     */
    public static Cell cellAt(int index) {
        if (index <= 0) {
            return new Cell(0, 0);
        }
        int ring = 1;
        while ((long) (PER_RING * ring + 1) * (PER_RING * ring + 1) <= index) {
            ring++;
        }
        int inner = PER_RING * ring - 1;
        int along = index - inner * inner;
        int side = along / (PER_RING * ring);
        int step = along % (PER_RING * ring);
        return switch (side % SIDES) {
            case EAST_SIDE -> new Cell(ring, -ring + 1 + step);
            case SOUTH_SIDE -> new Cell(ring - 1 - step, ring);
            case WEST_SIDE -> new Cell(-ring, ring - 1 - step);
            default -> new Cell(-ring + 1 + step, -ring);
        };
    }

    /**
     * The ring a cell sits in: ring k holds the cells of the square 2k + 1
     * across that the square 2k - 1 across leaves out.
     *
     * @param index the cell's place along the spiral
     * @return the ring, 0 for the center
     */
    public static int ringOf(int index) {
        int ring = 0;
        while ((long) (PER_RING * ring + 1) * (PER_RING * ring + 1) <= index) {
            ring++;
        }
        return ring;
    }

    /**
     * The void a whole ring costs to grow.
     *
     * @param ring        the ring
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @return the ring's cost (mB)
     */
    static long ringCost(int ring, int base, int perDistance) {
        int inner = PER_RING * ring - 1;
        int outer = PER_RING * ring + 1;
        long cost = 0;
        for (int index = inner * inner; index < outer * outer; index++) {
            cost += costOf(index, base, perDistance);
        }
        return cost;
    }

    /**
     * The void a cell costs: the base, plus the step for each block of
     * Manhattan distance between the cell and the center.
     *
     * @param index       the cell's place along the spiral
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @return the cell's cost (mB)
     */
    public static long costOf(int index, int base, int perDistance) {
        Cell cell = cellAt(index);
        return base + (long) perDistance * (Math.abs(cell.x()) + Math.abs(cell.z()));
    }
}
