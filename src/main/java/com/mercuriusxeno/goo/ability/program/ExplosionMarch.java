package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rays a Goo explosion marches, apart from the level so the march is a
 * pure method. Vanilla's ray set, 0.3-block step and 0.225 bleed per step
 * stand, since the bleed gives the blast its reach; every ray starts at
 * exactly its power, and a block's resistance is diminished before the ray
 * pays it.
 * goo-ray-diminishes-block-resistance
 * preview-sphere-is-max-reach
 */
public final class ExplosionMarch {

    /** Vanilla's ray grid, the least any explosion marches. */
    private static final int VANILLA_RAY_GRID = 16;
    /** The widest gap, in blocks, between neighboring rays where they cross the reach. */
    private static final double RAY_GAP_AT_REACH = 0.5;
    private static final float STEP = 0.3F;
    private static final float BLEED_PER_STEP = 0.225F;
    private static final float TOLL_BASE = 0.3F;
    /** A 0.3-block step per 0.225 power bled is 4 blocks of reach per 3 power, kept exact in doubles. */
    private static final double REACH_BLOCKS = 4.0;
    private static final double REACH_POWER = 3.0;
    private static final double GRID_SPAN = 2.0;
    /** A point past the reach by more than a cell's diagonal lies in no cell whose center the reach holds. */
    private static final double CELL_DIAGONAL = Math.sqrt(3.0);
    private static final Map<Integer, List<Vec3>> RAY_DIRECTIONS = new ConcurrentHashMap<>();

    private ExplosionMarch() {
    }

    /**
     * Answers a block's explosion resistance by its position: empty for air,
     * which the ray crosses paying the bleed alone.
     */
    @FunctionalInterface
    public interface ResistanceLookup {

        /**
         * Reads the resistance at a cell.
         *
         * @param pos the cell
         * @return the resistance, empty for air
         */
        Optional<Float> resistanceAt(BlockPos pos);
    }

    /**
     * The farthest an open-air ray of this power reaches, which is the radius
     * of the sphere the explosion cuts at most and the preview draws.
     *
     * @param power the explosion power
     * @return the reach in blocks
     */
    public static double maxReach(float power) {
        return power * REACH_BLOCKS / REACH_POWER;
    }

    /**
     * Diminishes a block's resistance before a ray pays it, so weak blocks
     * cost no more than air and strong ones still hold.
     *
     * @param resistance the block's explosion resistance
     * @return max(0, sqrt(resistance) - 1)
     */
    public static float diminishedResistance(float resistance) {
        return Math.max(0F, (float) Math.sqrt(resistance) - 1F);
    }

    /**
     * Marches every ray from the center and collects the cells a ray still
     * held power in after paying for them, inside the max-reach sphere.
     *
     * @param center the explosion center
     * @param power  the power every ray starts with
     * @param lookup the resistance of each cell
     * @return the marked cells
     */
    public static Set<BlockPos> markedCells(Vec3 center, float power, ResistanceLookup lookup) {
        return markedCells(center, power, maxReach(power), lookup);
    }

    /**
     * Marches every ray from the center and collects the cells a ray still
     * held power in after paying for them, inside a sphere of the reach
     * given, so a blast can carry more power than its open-air reach spends
     * and still stop at the ring it draws.
     * blast-fills-its-drawn-radius
     *
     * @param center the explosion center
     * @param power  the power every ray starts with
     * @param reach  the radius no marked cell's center lies past
     * @param lookup the resistance of each cell
     * @return the marked cells
     */
    public static Set<BlockPos> markedCells(Vec3 center, float power, double reach, ResistanceLookup lookup) {
        Set<BlockPos> marked = new HashSet<>();
        for (Vec3 direction : RAY_DIRECTIONS.computeIfAbsent(rayGridFor(reach), ExplosionMarch::rayDirections)) {
            marchRay(center, direction, power, reach, lookup, marked);
        }
        return marked;
    }

    /**
     * The ray grid an explosion of this reach marches: vanilla's 16, or finer
     * where vanilla's rays would cross the reach more than half a block apart
     * and leave cells between them standing.
     * blast-fills-its-drawn-radius
     *
     * @param reach the explosion's reach in blocks
     * @return the cells along each edge of the grid whose shell the rays point at
     */
    static int rayGridFor(double reach) {
        int edge = (int) Math.ceil((reach + 1) * GRID_SPAN / RAY_GAP_AT_REACH);
        return Math.max(VANILLA_RAY_GRID, edge + 1);
    }

    /**
     * The unit vectors toward every cell on the outer shell of a grid
     * centered on the explosion, vanilla's ray set at a grid of 16.
     *
     * @param grid the cells along each edge of the grid
     * @return the ray directions
     */
    private static List<Vec3> rayDirections(int grid) {
        List<Vec3> directions = new ArrayList<>();
        int edge = grid - 1;
        for (int x = 0; x < grid; x++) {
            for (int y = 0; y < grid; y++) {
                for (int z = 0; z < grid; z++) {
                    if (isOnEdge(x, edge) || isOnEdge(y, edge) || isOnEdge(z, edge)) {
                        directions.add(new Vec3(gridToUnit(x, edge), gridToUnit(y, edge), gridToUnit(z, edge))
                                .normalize());
                    }
                }
            }
        }
        return List.copyOf(directions);
    }

    private static boolean isOnEdge(int coordinate, int edge) {
        return coordinate == 0 || coordinate == edge;
    }

    private static double gridToUnit(int coordinate, int edge) {
        return coordinate / (double) edge * GRID_SPAN - 1.0;
    }

    private static void marchRay(Vec3 center, Vec3 direction, float power, double reach,
                                 ResistanceLookup lookup, Set<BlockPos> marked) {
        Vec3 step = direction.scale(STEP);
        Vec3 at = center;
        double marchLimit = reach + CELL_DIAGONAL;
        for (float remaining = power; remaining > 0F && at.distanceTo(center) <= marchLimit;
             remaining -= BLEED_PER_STEP) {
            BlockPos cell = BlockPos.containing(at);
            Optional<Float> resistance = lookup.resistanceAt(cell);
            if (resistance.isPresent()) {
                remaining -= (diminishedResistance(resistance.get()) + TOLL_BASE) * STEP;
            }
            if (remaining > 0F && Vec3.atCenterOf(cell).distanceTo(center) <= reach) {
                marked.add(cell);
            }
            at = at.add(step);
        }
    }
}
