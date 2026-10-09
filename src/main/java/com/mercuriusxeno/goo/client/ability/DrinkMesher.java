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
 * the surface along the field's gradient, which is its normal, each
 * vertex reads how fast the skin there is moving along its normal from the
 * field a tick ahead, and each quad places its vertices on the texture in
 * one frame, so the whole drink is one smooth skin with no cragginess from
 * the grid that keeps moving between meshes and wears its texture whole.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkMesher {

    /** The grid's finest cell, in blocks; a waist of radius 0.1 is ten vertices round at it. */
    public static final double CELL = 1.0 / 16;
    /** How far a point is moved toward the mean of its neighbours before it is set back onto the surface. */
    static final double RELAX = 0.5;
    /** The fastest the skin is read moving along its normal, in blocks a tick, so a flat field cannot fling it. */
    static final double FASTEST = 0.1;
    /** The share of a lone surface's steepness under which the field is too flat to read the skin's pace from. */
    static final double TOO_FLAT = 0.5;
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
     * @param velocity blocks a tick the skin there is moving along the normal, outward above zero
     */
    public record Vertex(Vec3 point, Vec3 normal, DrinkField.Skeleton skeleton, DrinkStream.Ring ring,
                         double velocity) {
    }

    /**
     * One quad of the surface, its four vertices wound counter-clockwise seen
     * from outside, with each vertex's place on the texture read in the one
     * frame the whole quad shares.
     *
     * @param vertices the four
     * @param along    each vertex's blocks along the texture's first coordinate
     * @param around   each vertex's blocks along its second
     */
    public record Quad(Vertex[] vertices, double[] along, double[] around) {
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
     * @param next      the drink's skeletons a tick ahead, in the same order with the same bodies
     * @param cell      the grid's cell, in blocks
     * @param cells     each marked cell's bodies, packed
     * @param corners   the field at each corner read
     * @param points    each cell's point, where the surface crosses it
     * @param scratch   a scratch array as long as the skeletons the field is gathered in
     */
    private record Grid(List<DrinkField.Skeleton> skeletons, List<DrinkField.Skeleton> next, double cell,
                        Long2ObjectOpenHashMap<int[]> cells, Long2DoubleOpenHashMap corners,
                        Long2ObjectOpenHashMap<Vec3> points, double[] scratch) {

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

        /**
         * @param candidates the bodies that reach a point's cell
         * @param x          the point's x
         * @param y          its y
         * @param z          its z
         * @return the field there now
         */
        double valueAt(int[] candidates, double x, double y, double z) {
            return DrinkField.valueAt(skeletons, candidates, x, y, z, scratch);
        }
    }

    /**
     * Meshes a drink's surface.
     *
     * @param skeletons the drink's skeletons
     * @param next      the drink's skeletons a tick ahead, built on the same tree, so the skin's motion is read
     * @param cell      the grid's cell, in blocks
     * @return the surface's quads
     */
    public static List<Quad> mesh(List<DrinkField.Skeleton> skeletons, List<DrinkField.Skeleton> next, double cell) {
        Long2DoubleOpenHashMap corners = new Long2DoubleOpenHashMap();
        corners.defaultReturnValue(Double.NaN);
        Grid grid = new Grid(skeletons, next, cell, cellsNear(skeletons, next, cell), corners,
                new Long2ObjectOpenHashMap<>(), new double[skeletons.size()]);
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
        Long2ObjectOpenHashMap<Vertex> vertices = smoothed(faces, settle(grid, faces));
        return quadsOf(faces, vertices);
    }

    /**
     * Averages each vertex's pace with its neighbours' round every face it is
     * on, so one vertex's misread pace cannot raise a spine on its own.
     *
     * @param faces    the faces
     * @param vertices each cell's vertex
     * @return each cell's vertex with its pace smoothed
     */
    private static Long2ObjectOpenHashMap<Vertex> smoothed(List<Face> faces, Long2ObjectOpenHashMap<Vertex> vertices) {
        Long2DoubleOpenHashMap sums = new Long2DoubleOpenHashMap();
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        for (Face face : faces) {
            for (int corner = 0; corner < QUAD; corner++) {
                Vertex next = vertices.get(face.cells()[(corner + 1) % QUAD]);
                if (next != null) {
                    sums.addTo(face.cells()[corner], next.velocity());
                    counts.addTo(face.cells()[corner], 1);
                }
            }
        }
        Long2ObjectOpenHashMap<Vertex> smoothed = new Long2ObjectOpenHashMap<>();
        vertices.long2ObjectEntrySet().fastForEach(entry -> {
            Vertex vertex = entry.getValue();
            int count = counts.get(entry.getLongKey());
            double about = count == 0 ? vertex.velocity() : sums.get(entry.getLongKey()) / count;
            smoothed.put(entry.getLongKey(), new Vertex(vertex.point(), vertex.normal(), vertex.skeleton(),
                    vertex.ring(), (vertex.velocity() + about) * HALF));
        });
        return smoothed;
    }

    /**
     * Every cell the surface may cross, with the bodies that reach it now or
     * a tick ahead: within a body's reach and a cell outside its surface, and
     * no deeper than a cell inside it, since deeper the field is whole.
     *
     * @param skeletons the drink's skeletons
     * @param next      the drink's skeletons a tick ahead
     * @param cell      the grid's cell, in blocks
     * @return the cells, keyed, each with its bodies packed
     */
    static Long2ObjectOpenHashMap<int[]> cellsNear(List<DrinkField.Skeleton> skeletons,
                                                   List<DrinkField.Skeleton> next, double cell) {
        Long2ObjectOpenHashMap<int[]> cells = new Long2ObjectOpenHashMap<>();
        for (List<DrinkField.Skeleton> tick : List.of(skeletons, next)) {
            for (int index = 0; index < tick.size(); index++) {
                DrinkField.Skeleton skeleton = tick.get(index);
                for (int body = 0; body < skeleton.bodies(); body++) {
                    markBody(cells, cell, skeleton, body, DrinkField.candidate(index, body));
                }
            }
        }
        return cells;
    }

    /**
     * Marks every cell whose middle lies within a body's reach and a cell outside its surface, or a cell inside it.
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
                    if (distance >= -cell && distance <= reach) {
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
            known = grid.valueAt(candidates, at.x, at.y, at.z);
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
        double value = grid.valueAt(candidates, relaxed.x, relaxed.y, relaxed.z);
        double slope = gradient.lengthSqr();
        double step = slope == 0 ? 0 : Math.clamp((value - DrinkField.ISO) / slope, -grid.cell(), grid.cell());
        Vec3 point = relaxed.subtract(gradient.scale(step));
        DrinkField.Sample sample = DrinkField.sample(grid.skeletons(), candidates, point);
        if (sample.skeleton() == null || sample.ring() == null) {
            return null;
        }
        Vec3 normal = slope > 0 ? gradient.reverse().normalize() : new Vec3(0, 1, 0);
        return new Vertex(point, normal, sample.skeleton(), sample.ring(), velocityAt(grid, candidates, point,
                Math.sqrt(slope)));
    }

    /**
     * How fast the skin at a point is moving along its normal: the field's
     * rise there over the next tick divided by how steeply the field falls
     * off outward, since the surface stays where the field is the iso; read
     * as still where the field is too flat to divide by, as in the saddle
     * between two bodies.
     *
     * @param grid       the grid
     * @param candidates the bodies that reach the point's cell
     * @param point      a point of the surface
     * @param steepness  the field's gradient's length there
     * @return blocks a tick the skin moves outward there, inward below zero, within {@link #FASTEST}
     */
    private static double velocityAt(Grid grid, int[] candidates, Vec3 point, double steepness) {
        if (steepness < DrinkField.SLOPE * TOO_FLAT) {
            return 0;
        }
        double now = grid.valueAt(candidates, point.x, point.y, point.z);
        double ahead = DrinkField.valueAt(grid.next(), candidates, point.x, point.y, point.z, grid.scratch());
        return Math.clamp((ahead - now) / steepness, -FASTEST, FASTEST);
    }

    /**
     * @param grid       the grid
     * @param candidates the bodies that reach the point's cell
     * @param point      a point near the surface
     * @return the field's gradient there, read over half a cell either side
     */
    private static Vec3 gradientAt(Grid grid, int[] candidates, Vec3 point) {
        double step = grid.cell() * GRADIENT_STEP;
        double dx = grid.valueAt(candidates, point.x + step, point.y, point.z)
                - grid.valueAt(candidates, point.x - step, point.y, point.z);
        double dy = grid.valueAt(candidates, point.x, point.y + step, point.z)
                - grid.valueAt(candidates, point.x, point.y - step, point.z);
        double dz = grid.valueAt(candidates, point.x, point.y, point.z + step)
                - grid.valueAt(candidates, point.x, point.y, point.z - step);
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
                quads.add(quadOf(face.reversed() ? reversed(ring) : ring));
            }
        }
        return quads;
    }

    /**
     * @param ring the quad's four vertices, wound for the outside
     * @return the quad, each vertex placed on the texture in the frame of the first vertex's skeleton: the standing
     *         block's world axes where the quad is on the block, else along and round the stream
     */
    private static Quad quadOf(Vertex[] ring) {
        DrinkField.Skeleton skeleton = ring[0].skeleton();
        Vec3 facing = Vec3.ZERO;
        for (Vertex vertex : ring) {
            facing = facing.add(vertex.normal());
        }
        boolean onBlock = DrinkTexture.onBlock(skeleton, ring[0].point());
        double[] along = new double[QUAD];
        double[] around = new double[QUAD];
        for (int corner = 0; corner < QUAD; corner++) {
            DrinkTexture.Place place = onBlock ? DrinkTexture.onBlock(ring[corner].point(), facing)
                    : DrinkTexture.onStream(skeleton, ring[corner].point());
            along[corner] = place.along();
            around[corner] = place.around();
        }
        return new Quad(ring, along, around);
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
