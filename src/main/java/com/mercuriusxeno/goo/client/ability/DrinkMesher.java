package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Meshes an Unmake drink's surface from its field: the cells of a grid each
 * body reaches are marked with the bodies that reach them, each marked cell
 * the surface crosses gets one point at the mean of its edge crossings, each
 * grid edge the surface crosses gets one quad between the four cells about
 * it, every point is then relaxed toward its neighbours and set back onto
 * the surface along the field's gradient, which is its normal, so the whole
 * drink is one smooth skin with no cragginess from the grid.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkMesher {

    /** The grid's finest cell, in blocks; the surface reads no feature thinner than about two cells. */
    public static final double CELL = 1.0 / 12;
    /** How far a point is moved toward the mean of its neighbours before it is set back onto the surface. */
    static final double RELAX = 0.5;
    /** The step either side of a point the gradient is read over, as a share of the cell. */
    private static final double GRADIENT_STEP = 0.5;
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
     * @param carry    how much of the skin there rides the liquid between meshes, 0 on the standing block
     */
    public record Vertex(Vec3 point, Vec3 normal, DrinkField.Skeleton skeleton, DrinkStream.Ring ring, double carry) {
    }

    /**
     * One quad of the surface, its four vertices wound counter-clockwise seen from outside.
     *
     * @param vertices the four
     */
    public record Quad(Vertex[] vertices) {
    }

    /**
     * One quad before its vertices are settled: the four cells about a grid edge the surface crosses.
     *
     * @param cells    the four cells, in order round the edge
     * @param reversed whether the order runs the wrong way for the outside
     */
    private record Face(long[] cells, boolean reversed) {
    }

    /**
     * The grid about a drink: every marked cell with the bodies that reach it,
     * the field at every corner read so far, and every cell's point.
     *
     * @param skeletons the drink's skeletons
     * @param cell      the grid's cell, in blocks
     * @param cells     each marked cell's bodies, packed
     * @param corners   the field at each corner read
     * @param points    each cell's point, where the surface crosses it
     */
    private record Grid(List<DrinkField.Skeleton> skeletons, double cell, Long2ObjectOpenHashMap<int[]> cells,
                        Long2DoubleOpenHashMap corners, Long2ObjectOpenHashMap<Vec3> points) {

        /**
         * @param x a corner's x index
         * @param y its y index
         * @param z its z index
         * @return the field at that corner, or NaN where it was never read
         */
        double cornerAt(int x, int y, int z) {
            return corners.get(key(x, y, z));
        }

        /**
         * @param key    the cell
         * @param corner the corner's index, bit 0 along x, bit 1 along y, bit 2 along z
         * @return the corner's world position
         */
        Vec3 cornerOf(long key, int corner) {
            return new Vec3((xOf(key) + bitOf(corner, X)) * cell, (yOf(key) + bitOf(corner, Y)) * cell,
                    (zOf(key) + bitOf(corner, Z)) * cell);
        }
    }

    /**
     * Meshes a drink's surface.
     *
     * @param skeletons the drink's skeletons
     * @param cell      the grid's cell, in blocks
     * @return the surface's quads
     */
    public static List<Quad> mesh(List<DrinkField.Skeleton> skeletons, double cell) {
        Long2DoubleOpenHashMap corners = new Long2DoubleOpenHashMap();
        corners.defaultReturnValue(Double.NaN);
        Grid grid = new Grid(skeletons, cell, cellsNear(skeletons, cell), corners, new Long2ObjectOpenHashMap<>());
        grid.cells().long2ObjectEntrySet().fastForEach(entry -> {
            Vec3 point = pointOf(grid, entry.getLongKey(), entry.getValue());
            if (point != null) {
                grid.points().put(entry.getLongKey(), point);
            }
        });
        List<Face> faces = new ArrayList<>();
        grid.points().keySet().forEach(key -> {
            for (int axis = 0; axis < AXES; axis++) {
                faceAbout(grid, key, axis, faces);
            }
        });
        Long2ObjectOpenHashMap<Vertex> vertices = settle(grid, faces);
        return quadsOf(faces, vertices);
    }

    /**
     * Every cell within a body's reach and the margin of any body, with the bodies that reach it.
     *
     * @param skeletons the drink's skeletons
     * @param cell      the grid's cell, in blocks
     * @return the cells, keyed, each with its bodies packed
     */
    static Long2ObjectOpenHashMap<int[]> cellsNear(List<DrinkField.Skeleton> skeletons, double cell) {
        Long2ObjectOpenHashMap<int[]> cells = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < skeletons.size(); index++) {
            DrinkField.Skeleton skeleton = skeletons.get(index);
            for (int body = 0; body < skeleton.bodies(); body++) {
                markBody(cells, cell, skeleton, body, DrinkField.candidate(index, body));
            }
        }
        return cells;
    }

    /**
     * Marks every cell whose middle lies within a body's reach and a cell of its surface.
     *
     * @param cells     the cells marked so far
     * @param cell      the grid's cell, in blocks
     * @param skeleton  the body's skeleton
     * @param body      the body's index
     * @param candidate the body packed
     */
    private static void markBody(Long2ObjectOpenHashMap<int[]> cells, double cell, DrinkField.Skeleton skeleton,
                                 int body, int candidate) {
        Vec3 low = skeleton.lowOf(body);
        Vec3 high = skeleton.highOf(body);
        double reach = DrinkField.REACH + cell;
        int x0 = cellOf(low.x - reach, cell);
        int y0 = cellOf(low.y - reach, cell);
        int z0 = cellOf(low.z - reach, cell);
        int x1 = cellOf(high.x + reach, cell);
        int y1 = cellOf(high.y + reach, cell);
        int z1 = cellOf(high.z + reach, cell);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    double distance = skeleton.distanceTo(body, (x + HALF) * cell, (y + HALF) * cell, (z + HALF) * cell);
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
     * @param cell       the grid's cell, in blocks
     * @return the index of the cell holding it
     */
    static int cellOf(double coordinate, double cell) {
        return (int) Math.floor(coordinate / cell);
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

    private static double fieldAtCorner(Grid grid, long key, int corner, int[] candidates) {
        long cornerKey = key(xOf(key) + bitOf(corner, X), yOf(key) + bitOf(corner, Y), zOf(key) + bitOf(corner, Z));
        double known = grid.corners().get(cornerKey);
        if (Double.isNaN(known)) {
            Vec3 at = grid.cornerOf(key, corner);
            known = DrinkField.valueAt(grid.skeletons(), candidates, at.x, at.y, at.z);
            grid.corners().put(cornerKey, known);
        }
        return known;
    }

    /**
     * The surface's point in a cell: the mean of the points where the field
     * crosses {@link DrinkField#ISO} along the cell's edges.
     *
     * @param grid       the grid
     * @param key        the cell
     * @param candidates the bodies that reach the cell
     * @return the point, or null where the surface does not cross the cell
     */
    private static @Nullable Vec3 pointOf(Grid grid, long key, int[] candidates) {
        double[] values = new double[CORNERS];
        for (int corner = 0; corner < CORNERS; corner++) {
            values[corner] = fieldAtCorner(grid, key, corner, candidates) - DrinkField.ISO;
        }
        Vec3 sum = Vec3.ZERO;
        int crossings = 0;
        for (int[] edge : EDGES) {
            double a = values[edge[0]];
            double b = values[edge[1]];
            if (a < 0 != b < 0) {
                sum = sum.add(grid.cornerOf(key, edge[0]).lerp(grid.cornerOf(key, edge[1]), a / (a - b)));
                crossings++;
            }
        }
        return crossings == 0 ? null : sum.scale(1.0 / crossings);
    }

    /**
     * Keeps the face about the edge leaving a cell's origin corner along an
     * axis where the surface crosses it: between the four cells sharing the
     * edge, noted where its order runs the wrong way for the outside.
     *
     * @param grid  the grid
     * @param key   the cell
     * @param axis  the edge's axis
     * @param faces where the face goes
     */
    private static void faceAbout(Grid grid, long key, int axis, List<Face> faces) {
        int x = xOf(key);
        int y = yOf(key);
        int z = zOf(key);
        double origin = grid.cornerAt(x, y, z);
        double end = grid.cornerAt(x + ALONG[axis][X], y + ALONG[axis][Y], z + ALONG[axis][Z]);
        boolean crosses = !Double.isNaN(origin) && !Double.isNaN(end) && origin < DrinkField.ISO != end < DrinkField.ISO;
        long[] ring = crosses ? ringAbout(grid, key, axis) : null;
        if (ring != null) {
            faces.add(new Face(ring, origin < DrinkField.ISO));
        }
    }

    /**
     * @param grid the grid
     * @param key  the cell
     * @param axis the edge's axis
     * @return the four cells about the edge, in order round it, or null where one has no point
     */
    private static long @Nullable [] ringAbout(Grid grid, long key, int axis) {
        long[] ring = new long[QUAD];
        ring[0] = key;
        for (int about = 0; about + 1 < QUAD; about++) {
            int[] offset = ABOUT_EDGE[axis][about];
            ring[about + 1] = key(xOf(key) + offset[X], yOf(key) + offset[Y], zOf(key) + offset[Z]);
            if (!grid.points().containsKey(ring[about + 1])) {
                return null;
            }
        }
        return ring;
    }

    /**
     * Settles every point into a vertex: moved {@link #RELAX} of the way to
     * the mean of its neighbours round every face it is on, then set back onto
     * the surface along the field's gradient, which is its normal.
     *
     * @param grid  the grid
     * @param faces the faces
     * @return each cell's vertex, cells where the field reaches no skeleton left out
     */
    private static Long2ObjectOpenHashMap<Vertex> settle(Grid grid, List<Face> faces) {
        Long2ObjectOpenHashMap<Vec3> sums = new Long2ObjectOpenHashMap<>();
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        for (Face face : faces) {
            for (int corner = 0; corner < QUAD; corner++) {
                long cell = face.cells()[corner];
                long next = face.cells()[(corner + 1) % QUAD];
                sums.merge(cell, grid.points().get(next), Vec3::add);
                counts.addTo(cell, 1);
            }
        }
        Long2ObjectOpenHashMap<Vertex> vertices = new Long2ObjectOpenHashMap<>();
        grid.points().long2ObjectEntrySet().fastForEach(entry -> {
            long cell = entry.getLongKey();
            Vec3 point = entry.getValue();
            Vec3 mean = counts.get(cell) == 0 ? point : sums.get(cell).scale(1.0 / counts.get(cell));
            Vertex vertex = vertexAt(grid, point.lerp(mean, RELAX), grid.cells().get(cell));
            if (vertex != null) {
                vertices.put(cell, vertex);
            }
        });
        return vertices;
    }

    /**
     * @param grid       the grid
     * @param relaxed    a point near the surface
     * @param candidates the bodies that reach its cell
     * @return the vertex where the point lands on the surface, or null where the field reaches no skeleton
     */
    private static @Nullable Vertex vertexAt(Grid grid, Vec3 relaxed, int[] candidates) {
        Vec3 gradient = gradientAt(grid, candidates, relaxed);
        double value = DrinkField.valueAt(grid.skeletons(), candidates, relaxed.x, relaxed.y, relaxed.z);
        double slope = gradient.lengthSqr();
        double step = slope == 0 ? 0 : Math.clamp((value - DrinkField.ISO) / slope, -grid.cell(), grid.cell());
        Vec3 point = relaxed.subtract(gradient.scale(step));
        DrinkField.Sample sample = DrinkField.sample(grid.skeletons(), candidates, point);
        if (sample.skeleton() == null || sample.ring() == null) {
            return null;
        }
        Vec3 normal = slope > 0 ? gradient.reverse().normalize() : new Vec3(0, 1, 0);
        return new Vertex(point, normal, sample.skeleton(), sample.ring(), sample.onBlock() ? 0 : sample.ring().carry());
    }

    /**
     * @param grid       the grid
     * @param candidates the bodies that reach the point's cell
     * @param point      a point near the surface
     * @return the field's gradient there, read over half a cell either side
     */
    private static Vec3 gradientAt(Grid grid, int[] candidates, Vec3 point) {
        double step = grid.cell() * GRADIENT_STEP;
        List<DrinkField.Skeleton> skeletons = grid.skeletons();
        double dx = DrinkField.valueAt(skeletons, candidates, point.x + step, point.y, point.z)
                - DrinkField.valueAt(skeletons, candidates, point.x - step, point.y, point.z);
        double dy = DrinkField.valueAt(skeletons, candidates, point.x, point.y + step, point.z)
                - DrinkField.valueAt(skeletons, candidates, point.x, point.y - step, point.z);
        double dz = DrinkField.valueAt(skeletons, candidates, point.x, point.y, point.z + step)
                - DrinkField.valueAt(skeletons, candidates, point.x, point.y, point.z - step);
        return new Vec3(dx, dy, dz).scale(HALF / step);
    }

    private static List<Quad> quadsOf(List<Face> faces, Long2ObjectOpenHashMap<Vertex> vertices) {
        List<Quad> quads = new ArrayList<>(faces.size());
        for (Face face : faces) {
            Vertex[] ring = new Vertex[QUAD];
            boolean whole = true;
            for (int corner = 0; corner < QUAD; corner++) {
                ring[corner] = vertices.get(face.cells()[corner]);
                whole &= ring[corner] != null;
            }
            if (whole) {
                quads.add(new Quad(face.reversed() ? reversed(ring) : ring));
            }
        }
        return quads;
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
