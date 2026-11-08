package com.mercuriusxeno.goo.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/**
 * How far a chunk of blocks moves before the world stops it, by its own
 * shape: each block's cell against the world's collision shapes, one axis at a
 * time, the vertical first as vanilla moves an entity, so a cross-shaped peg
 * drops into its own cross-shaped hole instead of resting on the rim the way
 * one box around the whole chunk would, and it never passes through a block.
 * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
 */
public final class HunkCollision {

    private HunkCollision() {
    }

    /**
     * The part of a move the world allows a chunk.
     *
     * @param level the level
     * @param cells the chunk's block cells where it stands
     * @param move  the move wanted
     * @return the move allowed, each axis cut where a block of the chunk would strike the world
     */
    public static Vec3 allowed(Level level, List<AABB> cells, Vec3 move) {
        return allowed(level, cells, move, List.of());
    }

    /**
     * The part of a move the world allows a chunk, with more boxes standing solid besides the world's blocks.
     *
     * @param level the level
     * @param cells the chunk's block cells where it stands
     * @param move  the move wanted
     * @param solid boxes that stop the chunk as blocks do, such as its own hole while it is held over it
     * @return the move allowed, each axis cut where a block of the chunk would strike the world or a solid box
     */
    public static Vec3 allowed(Level level, List<AABB> cells, Vec3 move, List<AABB> solid) {
        double dy = along(level, cells, Direction.Axis.Y, move.y, solid);
        List<AABB> moved = shifted(cells, 0, dy, 0);
        if (Math.abs(move.x) >= Math.abs(move.z)) {
            double dx = along(level, moved, Direction.Axis.X, move.x, solid);
            double dz = along(level, shifted(moved, dx, 0, 0), Direction.Axis.Z, move.z, solid);
            return new Vec3(dx, dy, dz);
        }
        double dz = along(level, moved, Direction.Axis.Z, move.z, solid);
        double dx = along(level, shifted(moved, 0, 0, dz), Direction.Axis.X, move.x, solid);
        return new Vec3(dx, dy, dz);
    }

    private static double along(Level level, List<AABB> cells, Direction.Axis axis, double distance,
                                List<AABB> solid) {
        double allowed = distance;
        for (AABB cell : cells) {
            if (allowed == 0) {
                return 0;
            }
            AABB swept = cell.expandTowards(axisVector(axis, allowed));
            List<VoxelShape> shapes = new ArrayList<>();
            level.getBlockCollisions(null, swept).forEach(shapes::add);
            solid.stream().filter(swept::intersects).map(Shapes::create).forEach(shapes::add);
            allowed = Shapes.collide(axis, cell, shapes, allowed);
        }
        return allowed;
    }

    private static Vec3 axisVector(Direction.Axis axis, double distance) {
        return switch (axis) {
            case X -> new Vec3(distance, 0, 0);
            case Y -> new Vec3(0, distance, 0);
            case Z -> new Vec3(0, 0, distance);
        };
    }

    private static List<AABB> shifted(List<AABB> cells, double x, double y, double z) {
        return cells.stream().map(cell -> cell.move(x, y, z)).toList();
    }
}
