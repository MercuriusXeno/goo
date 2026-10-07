package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    private static final int RAY_GRID = 16;
    private static final int RAY_GRID_EDGE = RAY_GRID - 1;
    private static final float STEP = 0.3F;
    private static final float BLEED_PER_STEP = 0.225F;
    private static final float TOLL_BASE = 0.3F;
    /** A 0.3-block step per 0.225 power bled is 4 blocks of reach per 3 power, kept exact in doubles. */
    private static final double REACH_BLOCKS = 4.0;
    private static final double REACH_POWER = 3.0;
    private static final double GRID_SPAN = 2.0;
    private static final List<Vec3> RAY_DIRECTIONS = rayDirections();

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
        double reach = maxReach(power);
        Set<BlockPos> marked = new HashSet<>();
        for (Vec3 direction : RAY_DIRECTIONS) {
            marchRay(center, direction, power, reach, lookup, marked);
        }
        return marked;
    }

    /**
     * Vanilla's ray set: the unit vectors toward every cell on the outer
     * shell of a 16x16x16 grid centered on the explosion.
     *
     * @return the ray directions
     */
    private static List<Vec3> rayDirections() {
        List<Vec3> directions = new ArrayList<>();
        for (int cell = 0; cell < RAY_GRID * RAY_GRID * RAY_GRID; cell++) {
            int x = cell / (RAY_GRID * RAY_GRID);
            int y = cell / RAY_GRID % RAY_GRID;
            int z = cell % RAY_GRID;
            if (isOnShell(x, y, z)) {
                directions.add(new Vec3(gridToUnit(x), gridToUnit(y), gridToUnit(z)).normalize());
            }
        }
        return List.copyOf(directions);
    }

    private static boolean isOnShell(int x, int y, int z) {
        return isOnEdge(x) || isOnEdge(y) || isOnEdge(z);
    }

    private static boolean isOnEdge(int coordinate) {
        return coordinate == 0 || coordinate == RAY_GRID_EDGE;
    }

    private static double gridToUnit(int coordinate) {
        return coordinate / (double) RAY_GRID_EDGE * GRID_SPAN - 1.0;
    }

    private static void marchRay(Vec3 center, Vec3 direction, float power, double reach,
                                 ResistanceLookup lookup, Set<BlockPos> marked) {
        Vec3 step = direction.scale(STEP);
        Vec3 at = center;
        for (float remaining = power; remaining > 0F; remaining -= BLEED_PER_STEP) {
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
