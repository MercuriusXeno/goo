package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.joml.Vector3fc;
import java.util.EnumSet;
import java.util.Set;

/**
 * The heights a slab of a goo block stands out from one of its surfaces, its
 * floor, a wall or its ceiling, read from the shares and solid blocks around
 * it, in layers out from that surface at a 4 by 4 grid of points 0, 4, 12 and
 * 16 texels in along each of the surface's two axes: the middle 8 by 8 texels
 * stand at the block's share on that surface, each edge at the average of
 * that share and the share the block across it holds on the same surface,
 * and each corner at the average of the four blocks meeting there, nothing
 * counting as zero. Against a solid block an edge keeps the block's own
 * share, and a solid block stays out of a corner's average. On the floor, a
 * block standing on a full block of its goo is one column with it, so the
 * heights compared are the columns' tops; no height stands under the block's
 * own floor, where the full block under it draws its own rims.
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 * attached-goo-draws-per-attachment-and-divides-its-volume
 * stacked-goo-cells-share-one-slime-core
 */
public final class SlimeTop {

    /** Where the heights stand along each axis, in model pixels: each edge and each edge of the middle. */
    static final float[] GRID = {0f, 4f, 12f, 16f};
    /** The layers a full block holds. */
    private static final int FULL = VolumeCellBlock.MAX_LAYERS;
    /** The last grid index along an axis. */
    private static final int LAST = GRID.length - 1;
    /** The step toward the low end of an axis. */
    private static final int LOW_SIDE = -1;

    private final Plane plane;
    private final double[] heights;
    private final Set<Direction> rims;
    private final boolean covered;

    private SlimeTop(Plane plane, double[] heights, Set<Direction> rims, boolean covered) {
        this.plane = plane;
        this.heights = heights;
        this.rims = rims;
        this.covered = covered;
    }

    /**
     * The top a block's floor slab draws, read from the cells around it: its
     * grid runs east along index a and south along index b.
     *
     * @param around the cells of the block's goo and the solid blocks around it
     * @return its heights, its rims and whether goo of its type covers it
     */
    public static SlimeTop of(LayersAround around) {
        return build(around, Plane.of(Direction.DOWN), around.layersAt(0, LOW_SIDE, 0) == FULL,
                around.layersAt(0, 1, 0) > 0);
    }

    /**
     * The slab a block draws on a wall or under its ceiling: on a north or
     * south wall its grid runs east along index a and up along index b, on
     * an east or west wall south and up, and under a ceiling east and south.
     *
     * @param around  the cells of the block's goo and the solid blocks around it
     * @param surface the side the slab clings to, UP for the ceiling
     * @return its heights, out from that surface, and its rims
     */
    public static SlimeTop onSurface(LayersAround around, Direction surface) {
        return build(around, Plane.of(surface), false, false);
    }

    /**
     * @param around  the cells around the block
     * @param plane   the slab's surface and axes
     * @param column  whether the block's floor slab stands on a full block of its goo
     * @param covered whether goo of its type stands on its floor slab
     * @return the slab's heights and rims
     */
    private static SlimeTop build(LayersAround around, Plane plane, boolean column, boolean covered) {
        double own = around.shareOn(0, 0, 0, plane.surface());
        double[] heights = new double[GRID.length * GRID.length];
        for (int a = 0; a < GRID.length; a++) {
            for (int b = 0; b < GRID.length; b++) {
                heights[a * GRID.length + b] = covered ? own
                        : Math.max(0, heightOf(around, plane, own, column, stepAt(a), stepAt(b)));
            }
        }
        Set<Direction> rims = EnumSet.noneOf(Direction.class);
        for (Direction edge : plane.edges()) {
            if (!continuesAcross(around, plane, column, edge.getUnitVec3i())) {
                rims.add(edge);
            }
        }
        return new SlimeTop(plane, heights, rims, covered);
    }

    /**
     * @param a the grid index along the surface's first axis, 0 to 3
     * @param b the grid index along its second axis, 0 to 3
     * @return the slab's height there, in layers out from its surface
     */
    public double heightAt(int a, int b) {
        return heights[a * GRID.length + b];
    }

    /**
     * The slab's height at any point, read straight between the grid's points.
     *
     * @param a model pixels along the surface's first axis from its low end
     * @param b model pixels along its second axis
     * @return the height there, in layers out from the surface
     */
    public double heightAtPixel(float a, float b) {
        int i = segmentOf(a);
        int j = segmentOf(b);
        double across = (a - GRID[i]) / (GRID[i + 1] - GRID[i]);
        double down = (b - GRID[j]) / (GRID[j + 1] - GRID[j]);
        double low = heightAt(i, j) + (heightAt(i + 1, j) - heightAt(i, j)) * across;
        double high = heightAt(i, j + 1) + (heightAt(i + 1, j + 1) - heightAt(i, j + 1)) * across;
        return low + (high - low) * down;
    }

    /**
     * @return the surface the slab stands on and the two axes its grid runs along
     */
    public Plane plane() {
        return plane;
    }

    /**
     * @return the edges no slab of the block's goo continues past on its surface, each drawing a rim
     */
    public Set<Direction> rims() {
        return rims;
    }

    /**
     * @return true where goo of the block's type stands on its floor slab, so the column goes on and it draws no top
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
     * The step toward the edge a grid index stands on.
     *
     * @param index a grid index along an axis
     * @return the low side for the low edge, 1 for the high, 0 inside
     */
    private static int stepAt(int index) {
        return index == 0 ? LOW_SIDE : index == LAST ? 1 : 0;
    }

    /**
     * The average of the block's own share and the slabs of the blocks
     * meeting at a point, leaving out each solid block.
     *
     * @param around the cells around the block
     * @param plane  the slab's surface and axes
     * @param own    the block's share on that surface
     * @param column whether the block's floor slab stands on a full block of its goo
     * @param stepA  the point's step toward an edge along the first axis, or 0
     * @param stepB  its step along the second axis, or 0
     * @return the height there, in layers out from the surface
     */
    private static double heightOf(LayersAround around, Plane plane, double own, boolean column, int stepA,
                                   int stepB) {
        double sum = own;
        int count = 1;
        for (Vec3i offset : plane.meetingAt(stepA, stepB)) {
            Neighbour across = plane.surface() == Direction.DOWN ? floorNeighbourAt(around, column, offset)
                    : slabNeighbourAt(around, plane.surface(), offset);
            if (!across.solid()) {
                sum += across.top();
                count++;
            }
        }
        return sum / count;
    }

    /**
     * What stands across an edge or a corner of a wall or ceiling slab: a
     * solid block, or the share the block there holds on the same surface.
     *
     * @param around  the cells around the block
     * @param surface the side the slab clings to
     * @param offset  the block across
     * @return what stands there
     */
    private static Neighbour slabNeighbourAt(LayersAround around, Direction surface, Vec3i offset) {
        int dx = offset.getX();
        int dy = offset.getY();
        int dz = offset.getZ();
        double share = around.layersAt(dx, dy, dz) > 0 ? around.shareOn(dx, dy, dz, surface) : 0;
        return new Neighbour(share == 0 && around.solidAt(dx, dy, dz), share);
    }

    /**
     * What stands across an edge or a corner of the floor slab: a solid
     * block, or the top of the column there in layers above this block's
     * floor, 0 for air on the same floor.
     *
     * @param around the cells around the block
     * @param column whether the block stands on a full block of its goo
     * @param offset the block across
     * @return what stands there
     */
    private static Neighbour floorNeighbourAt(LayersAround around, boolean column, Vec3i offset) {
        int dx = offset.getX();
        int dz = offset.getZ();
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
     * Whether a slab of the block's goo continues across an edge on the same surface.
     *
     * @param around the cells around the block
     * @param plane  the slab's surface and axes
     * @param column whether the block's floor slab stands on a full block of its goo
     * @param offset the block across the edge
     * @return true where goo continues there or, for a column's top, beside the block under it
     */
    private static boolean continuesAcross(LayersAround around, Plane plane, boolean column, Vec3i offset) {
        int dx = offset.getX();
        int dy = offset.getY();
        int dz = offset.getZ();
        if (plane.surface() != Direction.DOWN) {
            return around.layersAt(dx, dy, dz) > 0 && around.shareOn(dx, dy, dz, plane.surface()) > 0;
        }
        return around.layersAt(dx, 0, dz) > 0 || column && around.layersAt(dx, LOW_SIDE, dz) > 0;
    }

    /**
     * The surface a slab stands on and the two axes its grid runs along,
     * each named by its positive direction.
     *
     * @param surface the side the slab clings to: DOWN for the floor, UP for the ceiling
     * @param first   the grid's first axis, its index a
     * @param second  the grid's second axis, its index b
     */
    public record Plane(Direction surface, Direction first, Direction second) {

        /**
         * @param surface the side the slab clings to: DOWN for the floor, UP for the ceiling
         * @return the plane: east and south for the floor and the ceiling, east or south and up for a wall
         */
        static Plane of(Direction surface) {
            if (surface.getAxis() == Direction.Axis.Y) {
                return new Plane(surface, Direction.EAST, Direction.SOUTH);
            }
            return new Plane(surface, surface.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH,
                    Direction.UP);
        }

        /**
         * @return the four edges of the slab, in the surface's plane
         */
        Set<Direction> edges() {
            return EnumSet.of(first, first.getOpposite(), second, second.getOpposite());
        }

        /**
         * The blocks a point meets beside this one.
         *
         * @param stepA the point's step toward an edge along the first axis, or 0
         * @param stepB its step along the second axis, or 0
         * @return none inside, one across an edge, three at a corner
         */
        Vec3i[] meetingAt(int stepA, int stepB) {
            Vec3i alongA = first.getUnitVec3i().multiply(stepA);
            Vec3i alongB = second.getUnitVec3i().multiply(stepB);
            if (stepA != 0 && stepB != 0) {
                return new Vec3i[] {alongA, alongB, alongA.offset(alongB)};
            }
            return stepA == 0 && stepB == 0 ? new Vec3i[0] : new Vec3i[] {alongA.offset(alongB)};
        }

        /**
         * @param pixels a point in the block, in model pixels
         * @return where it stands along the first axis, in model pixels
         */
        float firstOf(Vector3fc pixels) {
            return coordinateOf(first, pixels);
        }

        /**
         * @param pixels a point in the block, in model pixels
         * @return where it stands along the second axis, in model pixels
         */
        float secondOf(Vector3fc pixels) {
            return coordinateOf(second, pixels);
        }

        private static float coordinateOf(Direction axis, Vector3fc pixels) {
            return switch (axis.getAxis()) {
                case X -> pixels.x();
                case Y -> pixels.y();
                case Z -> pixels.z();
            };
        }
    }

    /**
     * What stands across an edge or a corner.
     *
     * @param solid whether a solid block stands there
     * @param top   the slab or column there, in layers out from this block's surface
     */
    private record Neighbour(boolean solid, double top) {
    }
}
