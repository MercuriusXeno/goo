package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.joml.Vector3fc;
import java.util.Arrays;
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
 * share, and a solid block stays out of a corner's average. On the floor,
 * each block reads only the blocks beside it at its own level, and a full
 * block with goo of its type on top is a whole cube: every height stands
 * full, and each side it shows over lower goo beside it is a step from that
 * goo's edge up to its top.
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 * full-goo-block-under-goo-is-a-whole-cube
 * attached-goo-draws-per-attachment-and-divides-its-volume
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

    private final LayersAround around;
    private final Plane plane;
    private final double[] heights;
    private final double[] slope;
    private final Set<Direction> rims;
    private final Set<Direction> joins;
    private final Set<Direction> steps;
    private final Footing footing;

    private SlimeTop(LayersAround around, Plane plane, double[] slope, Edges edges, Footing footing) {
        this.around = around;
        this.plane = plane;
        this.slope = slope;
        this.heights = footing.covered() ? cubeHeights() : slope;
        this.rims = edges.rims();
        this.joins = edges.joins();
        this.steps = edges.steps();
        this.footing = footing;
    }

    /**
     * The top a block's floor slab draws, read from the cells around it: its
     * grid runs east along index a and south along index b.
     *
     * @param around the cells of the block's goo and the solid blocks around it
     * @return its heights, its rims and whether goo of its type covers it
     */
    public static SlimeTop of(LayersAround around) {
        // full-goo-block-under-goo-is-a-whole-cube
        boolean covered = coveredAt(around, 0, 0);
        return build(around, Plane.of(Direction.DOWN), new Footing(covered,
                around.layersAt(0, LOW_SIDE, 0) == FULL));
    }

    /**
     * Whether the block at an offset on this block's level is a whole cube:
     * full, with goo of its type on top.
     * full-goo-block-under-goo-is-a-whole-cube
     *
     * @param around the cells around the block
     * @param dx     east of the block
     * @param dz     south of the block
     * @return true where that block is a whole cube
     */
    private static boolean coveredAt(LayersAround around, int dx, int dz) {
        return around.layersAt(dx, 0, dz) == FULL && around.layersAt(dx, 1, dz) > 0;
    }

    private static double[] cubeHeights() {
        double[] full = new double[GRID.length * GRID.length];
        Arrays.fill(full, FULL);
        return full;
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
        return build(around, Plane.of(surface), new Footing(false, false));
    }

    /**
     * @param around  the cells around the block
     * @param plane   the slab's surface and axes
     * @param footing whether goo of its type covers its floor slab and whether that slab stands on a full block of it
     * @return the slab's heights and rims
     */
    private static SlimeTop build(LayersAround around, Plane plane, Footing footing) {
        double own = around.shareOn(0, 0, 0, plane.surface());
        double[] slope = new double[GRID.length * GRID.length];
        for (int a = 0; a < GRID.length; a++) {
            for (int b = 0; b < GRID.length; b++) {
                slope[a * GRID.length + b] = heightOf(around, plane, own, stepAt(a), stepAt(b));
            }
        }
        return new SlimeTop(around, plane, slope, edgesOf(around, plane, footing.covered()), footing);
    }

    /**
     * Sorts a slab's edges by what meets them.
     *
     * @param around  the cells around the block
     * @param plane   the slab's surface and axes
     * @param covered whether the block is a whole cube
     * @return the edges drawing a rim, the edges joining a slab round an outside edge, and a cube's steps
     */
    private static Edges edgesOf(LayersAround around, Plane plane, boolean covered) {
        Set<Direction> rims = EnumSet.noneOf(Direction.class);
        Set<Direction> joins = EnumSet.noneOf(Direction.class);
        Set<Direction> steps = EnumSet.noneOf(Direction.class);
        for (Direction edge : plane.edges()) {
            Vec3i across = edge.getUnitVec3i();
            if (joinsRound(around, plane, edge)) {
                joins.add(edge);
            } else if (!continuesAcross(around, plane, across)) {
                rims.add(edge);
            } else if (covered && !coveredAt(around, across.getX(), across.getZ())) {
                // full-goo-block-under-goo-is-a-whole-cube: the cube's side shows above the lower goo beside it
                steps.add(edge);
            }
        }
        return new Edges(rims, joins, steps);
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
        return between(heights, a, b);
    }

    /**
     * The height the slab slopes to at a point by the average with the
     * blocks beside it, its drawn height save in a whole cube, which stands
     * full over it: there it is where the edge of the lower goo beside the
     * cube meets the cube's side.
     * goo-block-top-slopes-to-the-average-with-the-adjoining-block
     * full-goo-block-under-goo-is-a-whole-cube
     *
     * @param a model pixels along the surface's first axis from its low end
     * @param b model pixels along its second axis
     * @return the slope there, in layers out from the surface
     */
    public double slopeAtPixel(float a, float b) {
        return between(slope, a, b);
    }

    private static double between(double[] grid, float a, float b) {
        int i = segmentOf(a);
        int j = segmentOf(b);
        double across = (a - GRID[i]) / (GRID[i + 1] - GRID[i]);
        double down = (b - GRID[j]) / (GRID[j + 1] - GRID[j]);
        double low = grid[i * GRID.length + j] + (grid[(i + 1) * GRID.length + j] - grid[i * GRID.length + j]) * across;
        double high = grid[i * GRID.length + j + 1]
                + (grid[(i + 1) * GRID.length + j + 1] - grid[i * GRID.length + j + 1]) * across;
        return low + (high - low) * down;
    }

    /**
     * @return true where the floor slab stands on a full block of its goo, so its core runs on down into it
     */
    public boolean standsOnItsGoo() {
        return footing.onItsGoo();
    }

    /**
     * @return the floor slab of the block above, read from the same cells
     */
    public SlimeTop above() {
        return of(around.shiftedBy(0, 1, 0));
    }

    /**
     * @return the floor slab of the block under, read from the same cells
     */
    public SlimeTop under() {
        return of(around.shiftedBy(0, LOW_SIDE, 0));
    }

    /**
     * @return the edges where a whole cube's side shows above lower goo of its type beside it, each drawing a step
     */
    public Set<Direction> steps() {
        return steps;
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
     * @return the edges a slab of the block's goo on the next face of the same solid block joins round
     */
    public Set<Direction> joins() {
        return joins;
    }

    /**
     * Whether a corner of the slab is an inner corner of its goo: a slab
     * continues past both edges meeting there, and none past the corner, so
     * the goo's edge turns in round it, as round the corner a film makes
     * where it bends.
     * slime-core-slopes-with-the-pile-inside-its-contour
     *
     * @param alongFirst  the corner's edge across the plane's first axis
     * @param alongSecond its edge across the plane's second axis
     * @return true where the goo's edge turns round the corner
     */
    public boolean turnsInAt(Direction alongFirst, Direction alongSecond) {
        if (opens(alongFirst) || opens(alongSecond)) {
            return false;
        }
        Vec3i diagonal = alongFirst.getUnitVec3i().offset(alongSecond.getUnitVec3i());
        return !continuesAcross(around, plane, diagonal);
    }

    private boolean opens(Direction edge) {
        return rims.contains(edge) || joins.contains(edge);
    }

    /**
     * The slab joining this one round an outside edge, read from the same
     * cells: it stands in the space diagonal to this block across that edge,
     * on the face of the solid block this slab clings to that the edge turns onto.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param edge an edge this slab joins round
     * @return the slab across it, its heights read in its own block
     */
    public SlimeTop across(Direction edge) {
        Vec3i diagonal = edge.getUnitVec3i().offset(plane.surface().getUnitVec3i());
        LayersAround theirs = around.shiftedBy(diagonal.getX(), diagonal.getY(), diagonal.getZ());
        Direction surface = edge.getOpposite();
        return surface == Direction.DOWN ? of(theirs) : onSurface(theirs, surface);
    }

    /**
     * @return true where the block is a whole cube, full with goo of its type on top, so it draws no top
     */
    public boolean covered() {
        return footing.covered();
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
     * @param stepA  the point's step toward an edge along the first axis, or 0
     * @param stepB  its step along the second axis, or 0
     * @return the height there, in layers out from the surface
     */
    private static double heightOf(LayersAround around, Plane plane, double own, int stepA, int stepB) {
        double sum = own;
        int count = 1;
        for (Vec3i offset : plane.meetingAt(stepA, stepB)) {
            Neighbour across = neighbourAt(around, plane, offset);
            if (!across.solid()) {
                sum += across.top();
                count++;
            }
        }
        return sum / count;
    }

    /**
     * What stands across an edge or a corner of a slab, the slab round an
     * outside edge standing in for nothing across that edge.
     *
     * @param around the cells around the block
     * @param plane  the slab's surface and axes
     * @param offset the block across
     * @return what stands there
     */
    private static Neighbour neighbourAt(LayersAround around, Plane plane, Vec3i offset) {
        Neighbour across = plane.surface() == Direction.DOWN ? floorNeighbourAt(around, offset)
                : slabNeighbourAt(around, plane.surface(), offset);
        Direction edge = Direction.getNearest(offset.getX(), offset.getY(), offset.getZ(), null);
        boolean nothing = !across.solid() && across.top() == 0;
        return edge != null && nothing && joinsRound(around, plane, edge)
                ? new Neighbour(false, roundTheEdge(around, plane, edge)) : across;
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
     * What stands across an edge or a corner of the floor slab at the
     * block's own level: a solid block, a whole cube at its full height, the
     * floor share of any other block of its goo, or nothing for air.
     * full-goo-block-under-goo-is-a-whole-cube
     *
     * @param around the cells around the block
     * @param offset the block across
     * @return what stands there
     */
    private static Neighbour floorNeighbourAt(LayersAround around, Vec3i offset) {
        int dx = offset.getX();
        int dz = offset.getZ();
        if (around.layersAt(dx, 0, dz) > 0) {
            return new Neighbour(false, coveredAt(around, dx, dz) ? FULL : around.floorShareAt(dx, 0, dz));
        }
        return new Neighbour(around.solidAt(dx, 0, dz), 0);
    }

    /**
     * Whether a slab of the block's goo on the next face of the solid block
     * this slab clings to joins it round an edge: the space across the edge
     * is open, and the space diagonal to the block across it holds goo of
     * its type clinging to that next face.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param around the cells around the block
     * @param plane  the slab's surface and axes
     * @param edge   the edge, in the slab's plane
     * @return true where the two slabs join round the edge
     */
    private static boolean joinsRound(LayersAround around, Plane plane, Direction edge) {
        Vec3i across = edge.getUnitVec3i();
        Vec3i holding = plane.surface().getUnitVec3i();
        boolean open = around.layersAt(across.getX(), across.getY(), across.getZ()) == 0
                && !around.solidAt(across.getX(), across.getY(), across.getZ());
        return open && around.solidAt(holding.getX(), holding.getY(), holding.getZ())
                && roundTheEdge(around, plane, edge) > 0;
    }

    /**
     * The share the slab round an outside edge holds on the next face.
     *
     * @param around the cells around the block
     * @param plane  the slab's surface and axes
     * @param edge   the edge, in the slab's plane
     * @return its layers, 0 where none stands there
     */
    private static double roundTheEdge(LayersAround around, Plane plane, Direction edge) {
        Vec3i diagonal = edge.getUnitVec3i().offset(plane.surface().getUnitVec3i());
        int dx = diagonal.getX();
        int dy = diagonal.getY();
        int dz = diagonal.getZ();
        return around.layersAt(dx, dy, dz) > 0 ? around.shareOn(dx, dy, dz, edge.getOpposite()) : 0;
    }

    /**
     * Whether a slab of the block's goo continues across an edge on the same surface.
     *
     * @param around the cells around the block
     * @param plane  the slab's surface and axes
     * @param offset the block across the edge
     * @return true where goo continues there
     */
    private static boolean continuesAcross(LayersAround around, Plane plane, Vec3i offset) {
        int dx = offset.getX();
        int dy = offset.getY();
        int dz = offset.getZ();
        if (plane.surface() != Direction.DOWN) {
            return around.layersAt(dx, dy, dz) > 0 && around.shareOn(dx, dy, dz, plane.surface()) > 0;
        }
        return around.layersAt(dx, 0, dz) > 0;
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
     * What a block's floor slab stands between.
     *
     * @param covered  whether it is a whole cube, full with goo of its type on top
     * @param onItsGoo whether it stands on a full block of its goo
     */
    private record Footing(boolean covered, boolean onItsGoo) {
    }

    /**
     * A slab's edges by what meets them.
     *
     * @param rims  the edges nothing continues past, each drawing a rim
     * @param joins the edges a slab round an outside edge joins
     * @param steps the edges a whole cube shows a side over lower goo of its type past
     */
    private record Edges(Set<Direction> rims, Set<Direction> joins, Set<Direction> steps) {
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
