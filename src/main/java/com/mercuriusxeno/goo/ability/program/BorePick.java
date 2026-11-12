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
 * along the eye line from the eye to the reach, widening from the JSON's
 * near radius to its far radius, never wider than a block, so aiming where
 * four blocks meet drills four, at the edge between two drills two, and at
 * a block's middle drills one. The stopping block shields the
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
     * @param eye        the eye the line and the vortex run from
     * @param nearRadius the vortex's radius at the eye
     * @param farRadius  the vortex's radius at the reach; both 0 pick the eye line alone
     * @param aim        the stream's aim: its reach along the look
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block in the tag
     * @return the pick
     */
    static BorePick of(Vec3 eye, double nearRadius, double farRadius, ChannelAim aim, Predicate<BlockPos> air,
                       Predicate<BlockPos> breakable) {
        List<BlockPos> crossed = BoreStep.blocksAlong(eye, aim.aimPoint());
        int stop = stoppingIndex(crossed, air, breakable);
        List<BlockPos> line = crossed.subList(1, stop);
        Vec3 look = aim.aimPoint().subtract(eye).normalize();
        double shield = stop < crossed.size() ? depthOf(crossed.get(stop), eye, look) : Double.MAX_VALUE;
        Sight sight = new Sight(eye, look, shield, crossed.getFirst());
        List<List<BlockPos>> groups = lineGroups(line, breakable);
        Set<BlockPos> tunnel = new LinkedHashSet<>(line);
        for (BlockPos cell : vortexCells(new Widening(eye, aim.aimPoint(), nearRadius, farRadius))) {
            if (sight.reaches(cell) && tunnel.add(cell) && breakable.test(cell)) {
                groups.get(nearestByDepth(line, sight.depthOf(cell), sight)).add(cell);
            }
        }
        return new BorePick(groups, List.copyOf(tunnel));
    }

    /**
     * Where the bore looks from and how deep it reaches.
     *
     * @param eye     the eye
     * @param look    the unit look
     * @param shield  the stopping block's depth, past which nothing is picked
     * @param eyeCell the eye's own block, never picked
     */
    private record Sight(Vec3 eye, Vec3 look, double shield, BlockPos eyeCell) {

        double depthOf(BlockPos cell) {
            return BorePick.depthOf(cell, eye, look);
        }

        boolean reaches(BlockPos cell) {
            return !cell.equals(eyeCell) && depthOf(cell) < shield;
        }
    }

    /**
     * One group for each block the eye line crosses, holding the block where
     * it breaks; one empty group where the line crosses none, which the
     * vortex's blocks join.
     *
     * @param line      the line's blocks, nearest first
     * @param breakable whether a cell holds a block in the tag
     * @return the groups
     */
    private static List<List<BlockPos>> lineGroups(List<BlockPos> line, Predicate<BlockPos> breakable) {
        List<List<BlockPos>> groups = new ArrayList<>();
        for (BlockPos cell : line) {
            groups.add(new ArrayList<>(breakable.test(cell) ? List.of(cell) : List.of()));
        }
        if (groups.isEmpty()) {
            groups.add(new ArrayList<>());
        }
        return groups;
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
     * The vortex: a cone from its near end to its far end, its radius
     * widening from one to the other.
     *
     * @param near       the near end, at the eye
     * @param far        the far end, at the reach
     * @param nearRadius the radius at the near end
     * @param farRadius  the radius at the far end
     */
    record Widening(Vec3 near, Vec3 far, double nearRadius, double farRadius) {

        /**
         * Whether a point lies inside: between the ends, and nearer the axis
         * than the radius there, so a vortex whose edge only meets a block's
         * face misses the block.
         *
         * @param point the point
         * @return true for a point inside
         */
        boolean holds(Vec3 point) {
            double length = far.distanceTo(near);
            Vec3 axis = far.subtract(near).scale(1 / length);
            Vec3 offset = point.subtract(near);
            double along = offset.dot(axis);
            if (along < 0 || along > length) {
                return false;
            }
            double radius = nearRadius + (farRadius - nearRadius) * along / length;
            return offset.subtract(axis.scale(along)).length() < radius;
        }
    }

    /**
     * Every cell the vortex crosses: the cells whose sample points, faces
     * among them, lie inside it.
     *
     * @param vortex the vortex
     * @return the cells, each once
     */
    static List<BlockPos> vortexCells(Widening vortex) {
        double widest = Math.max(vortex.nearRadius(), vortex.farRadius());
        if (widest <= 0 || vortex.far().distanceTo(vortex.near()) <= 0) {
            return List.of();
        }
        AABB bounds = new AABB(vortex.near(), vortex.far()).inflate(widest);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (crosses(cell, vortex)) {
                cells.add(cell.immutable());
            }
        }
        return cells;
    }

    private static boolean crosses(BlockPos cell, Widening vortex) {
        double step = 1.0 / (CELL_SAMPLES - 1);
        for (int x = 0; x < CELL_SAMPLES; x++) {
            for (int y = 0; y < CELL_SAMPLES; y++) {
                for (int z = 0; z < CELL_SAMPLES; z++) {
                    if (vortex.holds(new Vec3(cell.getX() + x * step, cell.getY() + y * step,
                            cell.getZ() + z * step))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double depthOf(BlockPos cell, Vec3 eye, Vec3 look) {
        return new Vec3(cell.getX() + HALF, cell.getY() + HALF, cell.getZ() + HALF).subtract(eye).dot(look);
    }

    private static int nearestByDepth(List<BlockPos> line, double depth, Sight sight) {
        int nearest = 0;
        double closest = Double.MAX_VALUE;
        for (int index = 0; index < line.size(); index++) {
            double apart = Math.abs(sight.depthOf(line.get(index)) - depth);
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
