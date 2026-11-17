package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.Direction;
import java.util.EnumSet;
import java.util.Set;

/**
 * The heights a goo block's sloped top stands at, read from the volumes and
 * solid blocks around it, in layers above the block's floor at a 4 by 4 grid
 * of points 0, 4, 12 and 16 texels in along each axis: the middle 8 by 8
 * texels stand at the block's floor share, each edge at the average of that
 * share and the share of the block across it, and each corner at the average
 * of the four blocks meeting there, air and any other goo counting as
 * nothing. Against a solid block an edge keeps the block's own share, and a
 * solid block stays out of a corner's average. A block standing on a full
 * block of its goo is one column with it, so the heights compared are the
 * columns' tops; no height stands under the block's own floor, where the
 * full block under it draws its own rims.
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 * stacked-goo-cells-share-one-slime-core
 */
public final class SlimeTop {

    /** Where the heights stand along each axis, in model pixels: each edge and each edge of the middle. */
    static final float[] GRID = {0f, 4f, 12f, 16f};
    /** The layers a full block holds. */
    private static final int FULL = VolumeCellBlock.MAX_LAYERS;
    /** The last grid index along an axis, the east or south edge. */
    private static final int LAST = GRID.length - 1;
    /** The offset toward the west, the north or below: the low side of an axis. */
    private static final int LOW_SIDE = -1;

    private final double[] heights;
    private final Set<Direction> rims;
    private final boolean covered;

    private SlimeTop(double[] heights, Set<Direction> rims, boolean covered) {
        this.heights = heights;
        this.rims = rims;
        this.covered = covered;
    }

    /**
     * The top a block draws, read from the cells around it.
     *
     * @param around the cells of the block's goo and the solid blocks around it
     * @return its heights, its rims and whether goo of its type covers it
     */
    public static SlimeTop of(LayersAround around) {
        double own = around.floorShareAt(0, 0, 0);
        boolean column = around.layersAt(0, LOW_SIDE, 0) == FULL;
        boolean covered = around.layersAt(0, 1, 0) > 0;
        double[] heights = new double[GRID.length * GRID.length];
        for (int x = 0; x < GRID.length; x++) {
            for (int z = 0; z < GRID.length; z++) {
                heights[x * GRID.length + z] = covered ? own
                        : Math.max(0, heightOf(around, own, column, stepAt(x), stepAt(z)));
            }
        }
        Set<Direction> rims = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!continuesAcross(around, column, side.getStepX(), side.getStepZ())) {
                rims.add(side);
            }
        }
        return new SlimeTop(heights, rims, covered);
    }

    /**
     * @param x the grid index west to east, 0 to 3
     * @param z the grid index north to south, 0 to 3
     * @return the top's height there, in layers above the block's floor
     */
    public double heightAt(int x, int z) {
        return heights[x * GRID.length + z];
    }

    /**
     * The top's height at any point, read straight between the grid's points.
     *
     * @param x model pixels east of the block's west side
     * @param z model pixels south of its north side
     * @return the height there, in layers above the block's floor
     */
    public double heightAtPixel(float x, float z) {
        int i = segmentOf(x);
        int j = segmentOf(z);
        double across = (x - GRID[i]) / (GRID[i + 1] - GRID[i]);
        double down = (z - GRID[j]) / (GRID[j + 1] - GRID[j]);
        double north = heightAt(i, j) + (heightAt(i + 1, j) - heightAt(i, j)) * across;
        double south = heightAt(i, j + 1) + (heightAt(i + 1, j + 1) - heightAt(i, j + 1)) * across;
        return north + (south - north) * down;
    }

    /**
     * @return the sides no goo of the block's type continues past, each drawing a rim
     */
    public Set<Direction> rims() {
        return rims;
    }

    /**
     * @return true where goo of the block's type stands on it, so the column goes on and the block draws no top
     */
    public boolean covered() {
        return covered;
    }

    private static int segmentOf(float pixel) {
        int segment = 0;
        while (segment + 1 < LAST && pixel >= GRID[segment + 1]) {
            segment++;
        }
        return segment;
    }

    /**
     * The offset toward the edge a grid index stands on.
     *
     * @param index a grid index along an axis
     * @return the low side for the west or north edge, 1 for the east or south, 0 inside
     */
    private static int stepAt(int index) {
        return index == 0 ? LOW_SIDE : index == LAST ? 1 : 0;
    }

    /**
     * The average of the block's own share and the tops of the blocks
     * meeting at a point, leaving out each solid block.
     *
     * @param around the cells around the block
     * @param own    the block's floor share
     * @param column whether the block stands on a full block of its goo
     * @param dx     the point's offset toward the east edge, the west, or 0
     * @param dz     the point's offset toward the south edge, the north, or 0
     * @return the height there, in layers above the block's floor
     */
    private static double heightOf(LayersAround around, double own, boolean column, int dx, int dz) {
        double sum = own;
        int count = 1;
        for (int[] offset : meetingAt(dx, dz)) {
            Neighbour across = neighbourAt(around, column, offset[0], offset[1]);
            if (!across.solid()) {
                sum += across.top();
                count++;
            }
        }
        return sum / count;
    }

    /**
     * The blocks a point meets beside this one.
     *
     * @param dx the point's offset toward an east or west edge, or 0
     * @param dz the point's offset toward a south or north edge, or 0
     * @return none inside, one across an edge, three at a corner
     */
    private static int[][] meetingAt(int dx, int dz) {
        if (dx != 0 && dz != 0) {
            return new int[][] {{dx, 0}, {0, dz}, {dx, dz}};
        }
        return dx == 0 && dz == 0 ? new int[0][] : new int[][] {{dx, dz}};
    }

    /**
     * What stands across a side or a corner: a solid block, or the top of
     * the column there in layers above this block's floor, 0 for air on the
     * same floor.
     *
     * @param around the cells around the block
     * @param column whether the block stands on a full block of its goo
     * @param dx     east of the block
     * @param dz     south of the block
     * @return what stands there
     */
    private static Neighbour neighbourAt(LayersAround around, boolean column, int dx, int dz) {
        int layers = around.layersAt(dx, 0, dz);
        if (layers > 0) {
            boolean topped = layers == FULL && around.layersAt(dx, 1, dz) > 0;
            return new Neighbour(false, topped ? FULL + around.floorShareAt(dx, 1, dz) : around.floorShareAt(dx, 0, dz));
        }
        if (around.solidAt(dx, 0, dz)) {
            return new Neighbour(true, 0);
        }
        return new Neighbour(false, column ? besideTheBlockUnder(around, dx, dz) : 0);
    }

    /**
     * The top across from a column's top block where nothing stands beside
     * it, read beside the full block under it: its goo's share, or solid
     * ground level with this block's floor, or the column's base for air.
     *
     * @param around the cells around the block
     * @param dx     east of the block
     * @param dz     south of the block
     * @return the top there, in layers above this block's floor
     */
    private static double besideTheBlockUnder(LayersAround around, int dx, int dz) {
        if (around.layersAt(dx, LOW_SIDE, dz) > 0) {
            return around.floorShareAt(dx, LOW_SIDE, dz) - FULL;
        }
        return around.solidAt(dx, LOW_SIDE, dz) ? 0 : -FULL;
    }

    /**
     * Whether goo of the block's type continues across a side.
     *
     * @param around the cells around the block
     * @param column whether the block stands on a full block of its goo
     * @param dx     east of the block
     * @param dz     south of the block
     * @return true where goo stands beside it or, for a column's top, beside the block under it
     */
    private static boolean continuesAcross(LayersAround around, boolean column, int dx, int dz) {
        return around.layersAt(dx, 0, dz) > 0 || column && around.layersAt(dx, LOW_SIDE, dz) > 0;
    }

    /**
     * What stands across a side or a corner.
     *
     * @param solid whether a solid block stands there
     * @param top   the column's top there, in layers above this block's floor
     */
    private record Neighbour(boolean solid, double top) {
    }
}
