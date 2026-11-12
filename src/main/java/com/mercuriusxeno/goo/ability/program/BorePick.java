package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a held Bore breaks this tick: the breakable blocks its eye line
 * crosses out to the reach, short of the first solid block outside its tag,
 * and every breakable block its vortex crosses besides. The vortex is a cone
 * whose base, the JSON's radius wide, stands at the glove and whose tip
 * stands at the reach on the eye line. The stopping block shields the
 * vortex's blocks at its depth and beyond, as it shields the line's. The
 * blocks break in groups by depth along the look, each vortex block with
 * the eye line block nearest its depth, so a nearer block gains progress
 * quicker than a farther one.
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-breaks-a-15-degree-cone
 * decision bore-drills-the-eye-line-with-steady-progress
 *
 * @param groups the breakable blocks in groups, the nearest group first, each empty group taking no rank
 * @param tunnel every cell the line and the vortex cross short of the stopping block, air among them,
 *               where Bore strikes what stands
 */
record BorePick(List<List<BlockPos>> groups, List<BlockPos> tunnel) {

    /** Sample points across each axis of a cell, its faces among them, tested for lying in the vortex. */
    private static final int CELL_SAMPLES = 5;
    private static final double HALF = 0.5;

    /**
     * Picks what a bore breaks this tick.
     *
     * @param eye          the eye the line runs from
     * @param glove        the glove the vortex opens from
     * @param vortexRadius the vortex's radius at the glove; 0 picks the eye line alone
     * @param aim          the stream's aim: its reach along the look
     * @param air          whether a cell holds air
     * @param breakable    whether a cell holds a block in the tag
     * @return the pick
     */
    static BorePick of(Vec3 eye, Vec3 glove, double vortexRadius, ChannelAim aim, Predicate<BlockPos> air,
                       Predicate<BlockPos> breakable) {
        List<BlockPos> crossed = BoreStep.blocksAlong(eye, aim.aimPoint());
        int stop = stoppingIndex(crossed, air, breakable);
        List<BlockPos> line = crossed.subList(1, stop);
        Vec3 look = aim.aimPoint().subtract(eye).normalize();
        double shield = stop < crossed.size() ? depthOf(crossed.get(stop), eye, look) : Double.MAX_VALUE;
        List<List<BlockPos>> groups = new ArrayList<>();
        for (BlockPos cell : line) {
            groups.add(new ArrayList<>(breakable.test(cell) ? List.of(cell) : List.of()));
        }
        if (groups.isEmpty()) {
            groups.add(new ArrayList<>());
        }
        Set<BlockPos> tunnel = new LinkedHashSet<>(line);
        for (BlockPos cell : vortexCells(glove, aim.aimPoint(), vortexRadius)) {
            double depth = depthOf(cell, eye, look);
            if (cell.equals(crossed.getFirst()) || depth >= shield || !tunnel.add(cell) || !breakable.test(cell)) {
                continue;
            }
            groups.get(nearestByDepth(line, depth, eye, look)).add(cell);
        }
        return new BorePick(groups, List.copyOf(tunnel));
    }

    /**
     * The breakable blocks picked, nearest group first.
     *
     * @return the blocks
     */
    List<BlockPos> blocks() {
        return groups.stream().flatMap(List::stream).toList();
    }

    /**
     * Every cell the vortex crosses: the cells whose sample points, faces
     * among them, lie inside the cone.
     *
     * @param glove  the cone's base, at the glove
     * @param tip    the cone's tip
     * @param radius the cone's radius at the glove
     * @return the cells, each once
     */
    static List<BlockPos> vortexCells(Vec3 glove, Vec3 tip, double radius) {
        double length = tip.distanceTo(glove);
        if (radius <= 0 || length <= 0) {
            return List.of();
        }
        Vec3 axis = tip.subtract(glove).scale(1 / length);
        AABB bounds = new AABB(glove, tip).inflate(radius);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (crossesCone(cell, glove, axis, length, radius)) {
                cells.add(cell.immutable());
            }
        }
        return cells;
    }

    private static boolean crossesCone(BlockPos cell, Vec3 glove, Vec3 axis, double length, double radius) {
        double step = 1.0 / (CELL_SAMPLES - 1);
        for (int x = 0; x < CELL_SAMPLES; x++) {
            for (int y = 0; y < CELL_SAMPLES; y++) {
                for (int z = 0; z < CELL_SAMPLES; z++) {
                    Vec3 point = new Vec3(cell.getX() + x * step, cell.getY() + y * step, cell.getZ() + z * step);
                    if (inCone(point, glove, axis, length, radius)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether a point lies inside the cone: past its base, short of its
     * tip, and no farther off its axis than its radius there.
     *
     * @param point  the point
     * @param glove  the cone's base
     * @param axis   the unit axis from the base to the tip
     * @param length the cone's length
     * @param radius the cone's radius at the base
     * @return true for a point inside
     */
    static boolean inCone(Vec3 point, Vec3 glove, Vec3 axis, double length, double radius) {
        Vec3 offset = point.subtract(glove);
        double along = offset.dot(axis);
        if (along < 0 || along > length) {
            return false;
        }
        return offset.subtract(axis.scale(along)).length() <= radius * (1 - along / length);
    }

    private static double depthOf(BlockPos cell, Vec3 eye, Vec3 look) {
        return new Vec3(cell.getX() + HALF, cell.getY() + HALF, cell.getZ() + HALF).subtract(eye).dot(look);
    }

    private static int nearestByDepth(List<BlockPos> line, double depth, Vec3 eye, Vec3 look) {
        int nearest = 0;
        double closest = Double.MAX_VALUE;
        for (int index = 0; index < line.size(); index++) {
            double apart = Math.abs(depthOf(line.get(index), eye, look) - depth);
            if (apart < closest) {
                closest = apart;
                nearest = index;
            }
        }
        return nearest;
    }

    /**
     * Where the eye line meets the first solid block outside the tag, past
     * the eye's own block.
     *
     * @param line      the blocks the eye line crosses, the eye's own first
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the stopping block's index on the line, or the line's length where none stands
     */
    private static int stoppingIndex(List<BlockPos> line, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        for (int i = 1; i < line.size(); i++) {
            if (!air.test(line.get(i)) && !breakable.test(line.get(i))) {
                return i;
            }
        }
        return line.size();
    }
}
