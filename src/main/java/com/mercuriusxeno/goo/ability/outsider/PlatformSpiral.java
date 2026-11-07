package com.mercuriusxeno.goo.ability.outsider;

/**
 * The order Outsider's platform grows in, and what each cell costs. The
 * platform walks a square spiral out from the voidstone, cell 0: ring k holds
 * the 8k cells k blocks out, walked south down the east side, west along the
 * south side, north up the west side and east along the north side, so each
 * cell shares an edge with the one before and a room's starting 3 by 3 is
 * cells 0 to 8. The next cell follows from the platform's cell count alone.
 * A grown cell costs a base plus a little for each block of Manhattan
 * distance from the center, as the operator ruled: the platform grows fast
 * early and only slowly comes to cost more the farther out it reaches.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
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
     * The void a cell costs to grow: the base, plus the step for each block
     * of Manhattan distance between the cell and the center.
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
