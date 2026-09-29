package com.mercuriusxeno.goo.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared footprint math for rock, blaze and frost chain effects. The
 * tunnel bores 1x1 at one stack, then 3x3 at depths 1, 2, 4, 7 and 10
 * (decision tunnel-stays-3x3-ee-homage). Flat mode opens a round disc one
 * block of radius per throw on one layer.
 */
public final class ChainFootprint {

    /**
     * Tunnel depth by stack count, one to six stacks.
     */
    private static final int[] TUNNEL_DEPTHS = {1, 1, 2, 4, 7, 10};

    /**
     * Maximum tunnel depth, the ladder's last rung.
     */
    public static final int MAX_DEPTH = TUNNEL_DEPTHS[TUNNEL_DEPTHS.length - 1];

    /**
     * Maximum meaningful stack count, the ladder's length.
     */
    public static final int MAX_STACKS = TUNNEL_DEPTHS.length;
    /**
     * Area mode: 3x3 tunnel advancing along placed face axis.
     */
    public static final String AREA_TUNNEL = "tunnel";
    /**
     * Area mode: round disc, one layer deep.
     */
    public static final String AREA_FLAT_CIRCLE = "flat_circle";
    /**
     * Area mode: expanding sphere.
     */
    public static final String AREA_SPHERE = "sphere";
    /**
     * Half-width of the 3x3 grid.
     */
    private static final int GRID_HALF = 1;
    /**
     * Negative unit offset along an axis.
     */
    private static final int NEG = -1;
    /**
     * AABB expansion: blocks occupy full unit cubes.
     */
    private static final int BLOCK_SIZE = 1;
    /**
     * Array index for Z component in offset triples.
     */
    private static final int Z_INDEX = 2;
    /**
     * Cells across a round shape per block of radius, either side of the center.
     */
    private static final int DIAMETER_PER_RADIUS = 2;
    /**
     * Scale of a squared distance measured in half blocks, so r + 0.5 compares in integers.
     */
    private static final int HALVES_SQUARED = 4;

    private ChainFootprint() {
    }


    /**
     * Total blocks the tunnel bores at the given stack count: 1, 9, 18,
     * 36, 63 and 90 over six stacks.
     *
     * @param stacks blob stack count (1-based)
     * @return total block count
     */
    public static int totalBlocks(int stacks) {
        return layerFootprint(stacks).size() * tunnelDepth(stacks);
    }

    /**
     * Tunnel depth (layers into the wall) at the given stack count, read
     * from the ladder 1, 1, 2, 4, 7, 10 and held at its last rung past six.
     *
     * @param stacks blob stack count (1-based)
     * @return depth in layers
     */
    public static int tunnelDepth(int stacks) {
        int rung = Math.clamp(stacks, 1, MAX_STACKS) - 1;
        return TUNNEL_DEPTHS[rung];
    }


    /**
     * Returns the 2D offsets for one tunnel-mode layer: the single block
     * at one stack, the 3x3 from two stacks on. Coordinates are
     * (perpA, perpB) relative to the layer center.
     *
     * @param stacks blob stack count (1-based)
     * @return list of [a, b] offset pairs
     */
    public static List<int[]> layerFootprint(int stacks) {
        return stacks == 1 ? singleBlock() : threeByThree();
    }

    /**
     * The disc radius at a stack count: one block per throw from the
     * start radius the ability JSON names (decision disc-opens-circularly-per-stack).
     *
     * @param stacks      blob stack count (1-based)
     * @param startRadius the radius of the first throw
     * @return the radius, never below zero
     */
    public static int discRadius(int stacks, int startRadius) {
        return Math.max(0, startRadius + stacks - 1);
    }

    /**
     * Returns the flat disc at a start radius of zero.
     *
     * @param stacks blob stack count (1-based)
     * @return list of [a, b] offset pairs
     */
    public static List<int[]> flatFootprint(int stacks) {
        return flatFootprint(stacks, 0);
    }

    /**
     * Returns the flat disc: every cell whose center lies under
     * {@code r + 0.5} of the center, for r the {@link #discRadius}.
     *
     * @param stacks      blob stack count (1-based)
     * @param startRadius the radius of the first throw
     * @return list of [a, b] offset pairs, ring by ring outward
     */
    public static List<int[]> flatFootprint(int stacks, int startRadius) {
        List<int[]> disc = new ArrayList<>();
        for (List<int[]> ring : flatRings(stacks, startRadius)) {
            disc.addAll(ring);
        }
        return disc;
    }

    /**
     * Returns the flat disc's rings at a start radius of zero.
     *
     * @param stacks blob stack count (1-based)
     * @return list of rings, each ring a list of [a, b] offset pairs
     */
    public static List<List<int[]>> flatRings(int stacks) {
        return flatRings(stacks, 0);
    }

    /**
     * Splits the flat disc into rings by integer distance: ring k holds
     * the cells with {@code floor(sqrt(a*a + b*b)) == k}, so ring 0 is the
     * center and the rings step outward, disjoint, uniting to the disc.
     *
     * @param stacks      blob stack count (1-based)
     * @param startRadius the radius of the first throw
     * @return list of rings, each ring a list of [a, b] offset pairs
     */
    public static List<List<int[]>> flatRings(int stacks, int startRadius) {
        int radius = discRadius(stacks, startRadius);
        List<List<int[]>> rings = new ArrayList<>(radius + 1);
        for (int k = 0; k <= radius; k++) {
            rings.add(new ArrayList<>());
        }
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                int squared = a * a + b * b;
                if (withinRound(squared, radius)) {
                    rings.get((int) Math.sqrt(squared)).add(new int[]{a, b});
                }
            }
        }
        return rings;
    }

    /**
     * Whether a cell center at a squared distance lies under {@code radius + 0.5},
     * in integers: {@code 4 * d2 < (2r + 1)^2}.
     *
     * @param squaredDistance the cell's squared distance from the center
     * @param radius          the round shape's radius
     * @return true when the cell belongs to the shape
     */
    private static boolean withinRound(int squaredDistance, int radius) {
        int diameter = DIAMETER_PER_RADIUS * radius + 1;
        return HALVES_SQUARED * squaredDistance < diameter * diameter;
    }


    private static List<int[]> singleBlock() {
        return List.of(new int[]{0, 0});
    }

    private static List<int[]> threeByThree() {
        List<int[]> grid = new ArrayList<>();
        for (int a = -GRID_HALF; a <= GRID_HALF; a++) {
            for (int b = -GRID_HALF; b <= GRID_HALF; b++) {
                grid.add(new int[]{a, b});
            }
        }
        return grid;
    }


    /**
     * Returns all 3D block offsets in the effect region, relative to
     * the marker position. Each offset is {dx, dy, dz} in world axes.
     * Layer 0 starts one step into the wall from the marker.
     *
     * @param stacks   blob stack count
     * @param flatMode true for flat mode
     * @param face     the placed face
     * @return list of {dx, dy, dz} offsets
     */
    public static List<int[]> computeRegionOffsets(int stacks, boolean flatMode, Direction face) {
        List<int[]> footprint = flatMode ? flatFootprint(stacks) : layerFootprint(stacks);
        int depth = flatMode ? 1 : tunnelDepth(stacks);
        Direction blastDir = face.getOpposite();
        return expandLayers(footprint, depth, blastDir);
    }

    /**
     * Returns all 3D block offsets in the effect region for the given area mode.
     * Dispatches to tunnel, flat circle, or sphere computation.
     *
     * @param stacks   blob stack count
     * @param areaMode "tunnel", "flat_circle", or "sphere"
     * @param face     the placed face
     * @return list of {dx, dy, dz} offsets
     */
    public static List<int[]> computeRegionOffsets(int stacks, String areaMode, Direction face) {
        if (AREA_SPHERE.equals(areaMode)) {
            int radius = AbilityMath.computeFreezeRadius(stacks);
            return computeSphereOffsets(radius, face);
        }
        boolean flat = AREA_FLAT_CIRCLE.equals(areaMode);
        return computeRegionOffsets(stacks, flat, face);
    }

    /**
     * Expands a 2D footprint into 3D offsets along the blast direction.
     *
     * @param footprint the 2D footprint offsets
     * @param depth     the number of layers
     * @param blastDir  the blast direction
     * @return list of 3D {dx, dy, dz} offsets
     */
    private static List<int[]> expandLayers(List<int[]> footprint, int depth, Direction blastDir) {
        Direction.Axis axis = blastDir.getAxis();
        int step = blastDir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : NEG;
        List<int[]> result = new ArrayList<>(footprint.size() * depth);
        for (int layer = 0; layer < depth; layer++) {
            int depthOffset = (layer + 1) * step;
            for (int[] fp : footprint) {
                result.add(mapToWorld(axis, fp[0], fp[1], depthOffset));
            }
        }
        return result;
    }

    /**
     * Maps a (perpA, perpB, depthAlong) triple to world (dx, dy, dz).
     *
     * @param axis the blast axis
     * @param a    first perpendicular offset
     * @param b    second perpendicular offset
     * @param d    depth offset along the blast axis
     * @return {dx, dy, dz} in world axes
     */
    private static int[] mapToWorld(Direction.Axis axis, int a, int b, int d) {
        return switch (axis) {
            case X -> new int[]{d, a, b};
            case Y -> new int[]{a, d, b};
            case Z -> new int[]{a, b, d};
        };
    }

    /**
     * Returns all 3D block offsets in a sphere centered one block into
     * the wall from the marker. Used by frost and other spheroid effects.
     *
     * @param radius the sphere radius in blocks
     * @param face   the placed face (determines center offset direction)
     * @return list of {dx, dy, dz} offsets relative to the marker
     */
    public static List<int[]> computeSphereOffsets(int radius, Direction face) {
        Direction blastDir = face.getOpposite();
        int cx = blastDir.getStepX();
        int cy = blastDir.getStepY();
        int cz = blastDir.getStepZ();
        int r2 = radius * radius;
        List<int[]> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        result.add(new int[]{dx + cx, dy + cy, dz + cz});
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns 3D offsets for a single spherical shell at the given radius.
     * Shell r contains all integer positions where r-1 < distance <= r,
     * computed as floor(sqrt(d2)) == r. Shell 0 is the origin block.
     * Shells 0..R union to the full solid sphere of radius R.
     *
     * @param shellRadius the shell radius (0 = origin only)
     * @return list of {dx, dy, dz} offsets
     */
    public static List<int[]> sphereShell(int shellRadius) {
        if (shellRadius == 0) {
            return List.of(new int[]{0, 0, 0});
        }
        int r2max = shellRadius * shellRadius;
        int r2min = (shellRadius - 1) * (shellRadius - 1);
        List<int[]> result = new ArrayList<>();
        for (int dx = -shellRadius; dx <= shellRadius; dx++) {
            collectShellSlice(result, dx, shellRadius, r2min, r2max);
        }
        return result;
    }

    /**
     * Collects all positions in one x-slice of a spherical shell.
     *
     * @param result      the output list
     * @param dx          the x offset
     * @param shellRadius the shell radius
     * @param r2min       the squared inner radius (exclusive)
     * @param r2max       the squared outer radius (inclusive)
     */
    private static void collectShellSlice(List<int[]> result, int dx,
                                          int shellRadius, int r2min, int r2max) {
        for (int dy = -shellRadius; dy <= shellRadius; dy++) {
            for (int dz = -shellRadius; dz <= shellRadius; dz++) {
                int d2 = dx * dx + dy * dy + dz * dz;
                if (d2 <= r2max && d2 > r2min) {
                    result.add(new int[]{dx, dy, dz});
                }
            }
        }
    }

    /**
     * Returns 3D offsets for a single spherical shell, translated so the
     * sphere center is one block into the wall from the marker.
     *
     * @param shellRadius the shell radius (0 = center block)
     * @param face        the placed face (determines center offset)
     * @return list of {dx, dy, dz} offsets relative to the marker
     */
    public static List<int[]> sphereShellOffsets(int shellRadius, Direction face) {
        Direction blastDir = face.getOpposite();
        int cx = blastDir.getStepX();
        int cy = blastDir.getStepY();
        int cz = blastDir.getStepZ();
        List<int[]> shell = sphereShell(shellRadius);
        List<int[]> result = new ArrayList<>(shell.size());
        for (int[] p : shell) {
            result.add(new int[]{p[0] + cx, p[1] + cy, p[Z_INDEX] + cz});
        }
        return result;
    }


    /**
     * Computes the AABB of all affected blocks relative to the marker
     * position. The marker sits in the air block adjacent to the wall;
     * layer 0 is one step into the wall from the marker.
     *
     * @param stacks   blob stack count
     * @param flatMode true for flat mode, false for tunnel
     * @param face     the face the marker was placed on
     * @return AABB in marker-local coordinates (marker at origin)
     */
    public static AABB computeBounds(int stacks, boolean flatMode, Direction face) {
        List<int[]> footprint = flatMode ? flatFootprint(stacks) : layerFootprint(stacks);
        int depth = flatMode ? 1 : tunnelDepth(stacks);
        Direction blastDir = face.getOpposite();

        int minA = Integer.MAX_VALUE;
        int maxA = Integer.MIN_VALUE;
        int minB = Integer.MAX_VALUE;
        int maxB = Integer.MIN_VALUE;
        for (int[] offset : footprint) {
            minA = Math.min(minA, offset[0]);
            maxA = Math.max(maxA, offset[0]);
            minB = Math.min(minB, offset[1]);
            maxB = Math.max(maxB, offset[1]);
        }

        int step = blastDir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : NEG;
        return mapToWorldBounds(blastDir.getAxis(), step, minA, maxA, minB, maxB, 1, depth);
    }

    /**
     * Maps 2D footprint bounds and depth range into a world-relative AABB
     * by routing the depth axis and perpendicular axes according to the
     * blast direction.
     *
     * @param axis       the blast axis (X, Y, or Z)
     * @param step       +1 or -1 along the axis
     * @param minA       minimum perpendicular-A offset
     * @param maxA       maximum perpendicular-A offset
     * @param minB       minimum perpendicular-B offset
     * @param maxB       maximum perpendicular-B offset
     * @param depthStart first layer offset along the blast direction
     * @param depthEnd   last layer offset along the blast direction
     * @return the world-relative AABB
     */
    private static AABB mapToWorldBounds(Direction.Axis axis, int step,
                                         int minA, int maxA, int minB, int maxB,
                                         int depthStart, int depthEnd) {
        int dMin = Math.min(depthStart * step, depthEnd * step);
        int dMax = Math.max(depthStart * step, depthEnd * step);
        return switch (axis) {
            case X -> new AABB(dMin, minA, minB,
                    dMax + BLOCK_SIZE, maxA + BLOCK_SIZE, maxB + BLOCK_SIZE);
            case Y -> new AABB(minA, dMin, minB,
                    maxA + BLOCK_SIZE, dMax + BLOCK_SIZE, maxB + BLOCK_SIZE);
            case Z -> new AABB(minA, minB, dMin,
                    maxA + BLOCK_SIZE, maxB + BLOCK_SIZE, dMax + BLOCK_SIZE);
        };
    }
}
