package com.mercuriusxeno.goo.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The struck face of a block's own outline shape: its center, where a face
 * marker sits, and the scale its marker draws at, the square root of the
 * face's longer side, so a face half a block wide marks at about 0.7 of a
 * full face's size. A block with no outline shape marks the whole cube face.
 * Decision aim-arc-ends-in-face-bullseye.
 *
 * @param center the face's center in world coordinates
 * @param scale  the marker's scale, one for a full face
 */
public record ShapeFace(Vec3 center, double scale) {

    private static final AABB FULL_CUBE = new AABB(0, 0, 0, 1, 1, 1);
    private static final double HALF = 0.5;

    /**
     * The struck face of the outline shape at a block.
     *
     * @param level the level the block stands in, or null when none is loaded
     * @param pos   the block
     * @param face  the struck face
     * @return the shape's face
     */
    public static ShapeFace at(@Nullable BlockGetter level, BlockPos pos, Direction face) {
        return of(pos, face, outlineBounds(level, pos));
    }

    /**
     * The struck face of a shape's bounds, given in the block's own
     * coordinates, zero to one.
     *
     * @param pos    the block
     * @param face   the struck face
     * @param bounds the outline's bounds in block coordinates
     * @return the shape's face
     */
    public static ShapeFace of(BlockPos pos, Direction face, AABB bounds) {
        double x = onAxis(face, Direction.Axis.X, bounds.minX, bounds.maxX);
        double y = onAxis(face, Direction.Axis.Y, bounds.minY, bounds.maxY);
        double z = onAxis(face, Direction.Axis.Z, bounds.minZ, bounds.maxZ);
        double longerSide = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != face.getAxis()) {
                longerSide = Math.max(longerSide, bounds.max(axis) - bounds.min(axis));
            }
        }
        return new ShapeFace(new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z), Math.sqrt(longerSide));
    }

    private static double onAxis(Direction face, Direction.Axis axis, double min, double max) {
        if (face.getAxis() != axis) {
            return (min + max) * HALF;
        }
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max : min;
    }

    private static AABB outlineBounds(@Nullable BlockGetter level, BlockPos pos) {
        if (level == null) {
            return FULL_CUBE;
        }
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        return shape.isEmpty() ? FULL_CUBE : shape.bounds();
    }
}
