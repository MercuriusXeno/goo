package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Meshes an Unmake drink's surface from its field: the cells of a grid each
 * body reaches are marked with the bodies that reach them, each marked cell
 * the surface crosses gets one vertex at the mean of its edge crossings with
 * the field's gradient for its normal, and each grid edge the surface
 * crosses gets one quad between the four cells about it, so the whole drink
 * is one smooth skin.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkMesher {

    /** The grid's cell, in blocks; the surface reads no feature thinner than about two cells. */
    public static final double CELL = 1.0 / 12;
    /** Blocks past a body's reach the grid is marked, so every crossing is found. */
    private static final double MARGIN = CELL;
    /** The step either side of a vertex the gradient is read over. */
    private static final double GRADIENT_STEP = CELL / 2;
    private static final double HALF = 0.5;
    private static final int CORNERS = 8;
    private static final int QUAD = 4;
    private static final int AXES = 3;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int BITS = 21;
    private static final int Y_SHIFT = BITS;
    private static final int X_SHIFT = 2 * BITS;
    private static final long MASK = (1L << BITS) - 1;
    private static final long OFFSET = 1L << (BITS - 1);
    /** The twelve edges of a cell, each as the indices of the two corners it joins. */
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5},
        {2, 6}, {3, 7}};
    /** For each axis, the step to the corner at the far end of a cell's edge along it. */
    private static final int[][] ALONG = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    /** For each axis, the offsets of the three other cells sharing the edge from a cell's origin along that axis. */
    private static final int[][][] ABOUT_EDGE = {
        {{0, -1, 0}, {0, -1, -1}, {0, 0, -1}},
        {{0, 0, -1}, {-1, 0, -1}, {-1, 0, 0}},
        {{-1, 0, 0}, {-1, -1, 0}, {0, -1, 0}},
    };

    private DrinkMesher() {
    }

    /**
     * One point of the surface.
     *
     * @param point    where it is, in the world
     * @param normal   the unit normal of the surface there, outward
     * @param skeleton the skeleton nearest it, whose block gives it its texture and its goo
     * @param ring     the ring of that skeleton nearest it, which gives it its flow and its place on the route
     */
    public record Vertex(Vec3 point, Vec3 normal, DrinkField.Skeleton skeleton, DrinkStream.Ring ring) {
    }

    /**
     * One quad of the surface, its four vertices wound counter-clockwise seen from outside.
     *
     * @param vertices the four
     */
    public record Quad(Vertex[] vertices) {
    }

    /**
     * The grid about a drink: every marked cell with the bodies that reach it,
     * the field at every corner read so far, and every cell's vertex.
     *
     * @param skeletons the drink's skeletons
     * @param cells     each marked cell's bodies, packed
     * @param corners   the field at each corner read
     * @param vertices  each cell's vertex
     */
    private record Grid(List<DrinkField.Skeleton> skeletons, Long2ObjectOpenHashMap<int[]> cells,
                        Long2DoubleOpenHashMap corners, Long2ObjectOpenHashMap<Vertex> vertices) {

        /**
         * @param x a corner's x index
         * @param y its y index
         * @param z its z index
         * @return the field at that corner, or NaN where it was never read
         */
        double cornerAt(int x, int y, int z) {
            return corners.get(key(x, y, z));
        }
    }

    /**
     * Meshes a drink's surface.
     *
     * @param skeletons the drink's skeletons
     * @return the surface's quads
     */
    public static List<Quad> mesh(List<DrinkField.Skeleton> skeletons) {
        Long2DoubleOpenHashMap corners = new Long2DoubleOpenHashMap();
        corners.defaultReturnValue(Double.NaN);
        Grid grid = new Grid(skeletons, cellsNear(skeletons), corners, new Long2ObjectOpenHashMap<>());
        grid.cells().long2ObjectEntrySet().fastForEach(cell -> {
            Vertex vertex = vertexOf(grid, cell.getLongKey(), cell.getValue());
            if (vertex != null) {
                grid.vertices().put(cell.getLongKey(), vertex);
            }
        });
        List<Quad> quads = new ArrayList<>();
        grid.vertices().keySet().forEach(cell -> {
            for (int axis = 0; axis < AXES; axis++) {
                quadAbout(grid, cell, axis, quads);
            }
        });
        return quads;
    }

    /**
     * Every cell within a body's reach and the margin of any body, with the bodies that reach it.
     *
     * @param skeletons the drink's skeletons
     * @return the cells, keyed, each with its bodies packed
     */
    static Long2ObjectOpenHashMap<int[]> cellsNear(List<DrinkField.Skeleton> skeletons) {
        Long2ObjectOpenHashMap<int[]> cells = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < skeletons.size(); index++) {
            DrinkField.Skeleton skeleton = skeletons.get(index);
            for (int body = 0; body < skeleton.bodies(); body++) {
                markBody(cells, skeleton, body, DrinkField.candidate(index, body));
            }
        }
        return cells;
    }

    /**
     * Marks every cell whose middle lies within a body's reach and the margin of its surface.
     *
     * @param cells     the cells marked so far
     * @param skeleton  the body's skeleton
     * @param body      the body's index
     * @param candidate the body packed
     */
    private static void markBody(Long2ObjectOpenHashMap<int[]> cells, DrinkField.Skeleton skeleton, int body,
                                 int candidate) {
        Vec3 low = skeleton.lowOf(body);
        Vec3 high = skeleton.highOf(body);
        double reach = DrinkField.REACH + MARGIN;
        int x0 = cellOf(low.x - reach);
        int y0 = cellOf(low.y - reach);
        int z0 = cellOf(low.z - reach);
        int x1 = cellOf(high.x + reach);
        int y1 = cellOf(high.y + reach);
        int z1 = cellOf(high.z + reach);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    double distance = skeleton.distanceTo(body, (x + HALF) * CELL, (y + HALF) * CELL, (z + HALF) * CELL);
                    if (Math.abs(distance) <= reach) {
                        cells.merge(key(x, y, z), new int[]{candidate}, DrinkMesher::joined);
                    }
                }
            }
        }
    }

    private static int[] joined(int[] some, int[] more) {
        int[] all = Arrays.copyOf(some, some.length + more.length);
        System.arraycopy(more, 0, all, some.length, more.length);
        return all;
    }

    /**
     * @param coordinate a world coordinate
     * @return the index of the cell holding it
     */
    static int cellOf(double coordinate) {
        return (int) Math.floor(coordinate / CELL);
    }

    /**
     * @param x the cell's x index
     * @param y its y index
     * @param z its z index
     * @return the cell's key, each index in 21 bits about the middle
     */
    static long key(int x, int y, int z) {
        long kx = (x + OFFSET) & MASK;
        long ky = (y + OFFSET) & MASK;
        long kz = (z + OFFSET) & MASK;
        return kx << X_SHIFT | ky << Y_SHIFT | kz;
    }

    private static int xOf(long key) {
        return (int) ((key >>> X_SHIFT) & MASK) - (int) OFFSET;
    }

    private static int yOf(long key) {
        return (int) ((key >>> Y_SHIFT) & MASK) - (int) OFFSET;
    }

    private static int zOf(long key) {
        return (int) (key & MASK) - (int) OFFSET;
    }

    /**
     * @param corner a corner's index
     * @param axis   an axis
     * @return 1 where the corner lies at the far side of the cell along the axis, else 0
     */
    private static int bitOf(int corner, int axis) {
        return (corner >> axis) & 1;
    }

    /**
     * @param key    the cell
     * @param corner the corner's index, bit 0 along x, bit 1 along y, bit 2 along z
     * @return the corner's world position
     */
    private static Vec3 cornerOf(long key, int corner) {
        return new Vec3((xOf(key) + bitOf(corner, X)) * CELL, (yOf(key) + bitOf(corner, Y)) * CELL,
                (zOf(key) + bitOf(corner, Z)) * CELL);
    }

    private static double fieldAtCorner(Grid grid, long key, int corner, int[] candidates) {
        long cornerKey = key(xOf(key) + bitOf(corner, X), yOf(key) + bitOf(corner, Y), zOf(key) + bitOf(corner, Z));
        double known = grid.corners().get(cornerKey);
        if (Double.isNaN(known)) {
            Vec3 at = cornerOf(key, corner);
            known = DrinkField.valueAt(grid.skeletons(), candidates, at.x, at.y, at.z);
            grid.corners().put(cornerKey, known);
        }
        return known;
    }

    /**
     * The surface's vertex in a cell: the mean of the points where the field
     * crosses {@link DrinkField#ISO} along the cell's edges.
     *
     * @param grid       the grid
     * @param key        the cell
     * @param candidates the bodies that reach the cell
     * @return the vertex, or null where the surface does not cross the cell
     */
    private static @Nullable Vertex vertexOf(Grid grid, long key, int[] candidates) {
        double[] values = new double[CORNERS];
        for (int corner = 0; corner < CORNERS; corner++) {
            values[corner] = fieldAtCorner(grid, key, corner, candidates) - DrinkField.ISO;
        }
        Vec3 point = crossingsMean(key, values);
        if (point == null) {
            return null;
        }
        DrinkField.Sample sample = DrinkField.sample(grid.skeletons(), candidates, point);
        if (sample.skeleton() == null || sample.ring() == null) {
            return null;
        }
        return new Vertex(point, normalAt(grid.skeletons(), candidates, point), sample.skeleton(), sample.ring());
    }

    /**
     * @param key    the cell
     * @param values the field less the iso at each corner
     * @return the mean of the edge crossings, or null where no edge crosses
     */
    private static @Nullable Vec3 crossingsMean(long key, double[] values) {
        Vec3 sum = Vec3.ZERO;
        int crossings = 0;
        for (int[] edge : EDGES) {
            double a = values[edge[0]];
            double b = values[edge[1]];
            if (a < 0 != b < 0) {
                sum = sum.add(cornerOf(key, edge[0]).lerp(cornerOf(key, edge[1]), a / (a - b)));
                crossings++;
            }
        }
        return crossings == 0 ? null : sum.scale(1.0 / crossings);
    }

    /**
     * @param skeletons  the drink's skeletons
     * @param candidates the bodies that reach the point's cell
     * @param point      a point of the surface
     * @return the unit normal there, outward: against the field's gradient
     */
    static Vec3 normalAt(List<DrinkField.Skeleton> skeletons, int[] candidates, Vec3 point) {
        double dx = DrinkField.valueAt(skeletons, candidates, point.x + GRADIENT_STEP, point.y, point.z)
                - DrinkField.valueAt(skeletons, candidates, point.x - GRADIENT_STEP, point.y, point.z);
        double dy = DrinkField.valueAt(skeletons, candidates, point.x, point.y + GRADIENT_STEP, point.z)
                - DrinkField.valueAt(skeletons, candidates, point.x, point.y - GRADIENT_STEP, point.z);
        double dz = DrinkField.valueAt(skeletons, candidates, point.x, point.y, point.z + GRADIENT_STEP)
                - DrinkField.valueAt(skeletons, candidates, point.x, point.y, point.z - GRADIENT_STEP);
        Vec3 gradient = new Vec3(dx, dy, dz);
        return gradient.lengthSqr() > 0 ? gradient.reverse().normalize() : new Vec3(0, 1, 0);
    }

    /**
     * Emits the quad about the edge leaving a cell's origin corner along an
     * axis where the surface crosses it: between the four cells sharing the
     * edge, wound to face the outside.
     *
     * @param grid  the grid
     * @param key   the cell
     * @param axis  the edge's axis
     * @param quads where the quad goes
     */
    private static void quadAbout(Grid grid, long key, int axis, List<Quad> quads) {
        int x = xOf(key);
        int y = yOf(key);
        int z = zOf(key);
        double origin = grid.cornerAt(x, y, z);
        double end = grid.cornerAt(x + ALONG[axis][X], y + ALONG[axis][Y], z + ALONG[axis][Z]);
        boolean crosses = !Double.isNaN(origin) && !Double.isNaN(end) && origin < DrinkField.ISO != end < DrinkField.ISO;
        Vertex[] ring = crosses ? ringAbout(grid, key, axis) : null;
        if (ring != null) {
            quads.add(origin < DrinkField.ISO ? new Quad(reversed(ring)) : new Quad(ring));
        }
    }

    /**
     * @param grid the grid
     * @param key  the cell
     * @param axis the edge's axis
     * @return the vertices of the four cells about the edge, in order round it, or null where one has none
     */
    private static Vertex @Nullable [] ringAbout(Grid grid, long key, int axis) {
        Vertex[] ring = new Vertex[QUAD];
        ring[0] = grid.vertices().get(key);
        for (int about = 0; about + 1 < QUAD; about++) {
            int[] offset = ABOUT_EDGE[axis][about];
            ring[about + 1] = grid.vertices().get(key(xOf(key) + offset[X], yOf(key) + offset[Y], zOf(key)
                    + offset[Z]));
            if (ring[about + 1] == null) {
                return null;
            }
        }
        return ring[0] == null ? null : ring;
    }

    private static Vertex[] reversed(Vertex[] ring) {
        Vertex[] reversed = new Vertex[ring.length];
        reversed[0] = ring[0];
        for (int index = 1; index < ring.length; index++) {
            reversed[index] = ring[ring.length - index];
        }
        return reversed;
    }
}
