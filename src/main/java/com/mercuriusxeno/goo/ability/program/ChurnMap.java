package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;

/**
 * The fixed one-to-one map between Churn's sixteen core cells and its
 * sixteen strip cells, in the plane of one layer. The core is the 4x4 square
 * at x and z 0..3; a strip of four lies along each side, north at z = -1,
 * east at x = 4, south at z = 4 and west at x = -1, so the footprint is a plus
 * with a thick middle and the strips never share a cell.
 * <p>
 * Each core edge cell maps to the strip cell beside it. Each core corner
 * touches two strips and goes clockwise to one strip end: north-west to the
 * north strip's west end, north-east to the east strip's north end,
 * south-east to the south strip's east end, south-west to the west strip's
 * south end. Each inner cell takes the strip end left over nearest it. The
 * map turns with the footprint under a quarter turn, so no side of the
 * column is favored, and a unit test proves it a bijection.
 * decision churn-rotates-a-plus-shaped-column
 */
public final class ChurnMap {

    /** Cells along one side of the core, and the length of one strip. */
    public static final int SIDE = 4;
    /** Cells in the core layer, and in the four strips together. */
    public static final int CELLS = SIDE * SIDE;
    private static final int LAST = SIDE - 1;
    /** The row or column just outside the core's low side, where the north and west strips lie. */
    private static final int OUTSIDE_LOW = -1;
    /** The core's two middle rows and columns, where its inner cells stand. */
    private static final int MIDDLE_LOW = 1;
    private static final int MIDDLE_HIGH = 2;
    /** The core cell the landed block stands in, on each horizontal axis. */
    private static final int LANDED_CELL = MIDDLE_LOW;

    /** Strip cells in order: north west to east, east north to south, south west to east, west north to south. */
    private static final int[][] STRIP_CELLS = new int[CELLS][];
    /** The strip index each core cell, indexed x + 4z, feeds. */
    private static final int[] STRIP_OF_CORE = new int[CELLS];
    private static final int NORTH = 0;
    private static final int EAST = SIDE;
    private static final int SOUTH = EAST + SIDE;
    private static final int WEST = SOUTH + SIDE;

    static {
        for (int k = 0; k < SIDE; k++) {
            STRIP_CELLS[NORTH + k] = new int[] {k, OUTSIDE_LOW};
            STRIP_CELLS[EAST + k] = new int[] {SIDE, k};
            STRIP_CELLS[SOUTH + k] = new int[] {k, SIDE};
            STRIP_CELLS[WEST + k] = new int[] {OUTSIDE_LOW, k};
        }
        for (int k = 1; k < LAST; k++) {
            STRIP_OF_CORE[core(k, 0)] = NORTH + k;
            STRIP_OF_CORE[core(LAST, k)] = EAST + k;
            STRIP_OF_CORE[core(k, LAST)] = SOUTH + k;
            STRIP_OF_CORE[core(0, k)] = WEST + k;
        }
        STRIP_OF_CORE[core(0, 0)] = NORTH;
        STRIP_OF_CORE[core(LAST, 0)] = EAST;
        STRIP_OF_CORE[core(LAST, LAST)] = SOUTH + LAST;
        STRIP_OF_CORE[core(0, LAST)] = WEST + LAST;
        STRIP_OF_CORE[core(MIDDLE_HIGH, MIDDLE_LOW)] = NORTH + LAST;
        STRIP_OF_CORE[core(MIDDLE_HIGH, MIDDLE_HIGH)] = EAST + LAST;
        STRIP_OF_CORE[core(MIDDLE_LOW, MIDDLE_HIGH)] = SOUTH;
        STRIP_OF_CORE[core(MIDDLE_LOW, MIDDLE_LOW)] = WEST;
    }

    private ChurnMap() {
    }

    /**
     * The north-west core cell of a column's top layer, the landed block
     * standing in one of the core's four middle cells.
     *
     * @param landed the block a churn landed on
     * @return the column's origin
     */
    public static BlockPos originUnder(BlockPos landed) {
        return landed.offset(-LANDED_CELL, 0, -LANDED_CELL);
    }

    /**
     * @param x the core cell's x, 0..3
     * @param z the core cell's z, 0..3
     * @return the core cell's index
     */
    public static int core(int x, int z) {
        return x + SIDE * z;
    }

    /**
     * @param coreIndex a core cell's index
     * @return its x and z in the layer
     */
    public static int[] coreCell(int coreIndex) {
        return new int[] {coreIndex % SIDE, coreIndex / SIDE};
    }

    /**
     * @param coreIndex a core cell's index
     * @return the x and z of the strip cell it feeds
     */
    public static int[] stripCellOf(int coreIndex) {
        return STRIP_CELLS[STRIP_OF_CORE[coreIndex]].clone();
    }
}
