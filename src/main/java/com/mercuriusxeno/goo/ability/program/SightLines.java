package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What an eye can see: a block when any part of a face turned toward the eye
 * is in view, a living thing when its middle is. A block is tested at the
 * middle and the corners of each face it turns to the eye, so a block plainly
 * in view at a grazing angle counts and one buried behind another does not.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class SightLines {

    /** How far in from a face's corners the corner sight lines aim, so they meet the face and not its edge. */
    private static final double CORNER_INSET = 0.1;
    private static final double HALF = 0.5;

    private SightLines() {
    }

    /**
     * Whether the eye sees any part of a block.
     *
     * @param level  the level
     * @param eye    the eye
     * @param pos    the block
     * @param viewer the entity whose eye it is, which the sight lines pass through
     * @return true when a face turned toward the eye is in view anywhere
     */
    public static boolean seesBlock(ServerLevel level, Vec3 eye, BlockPos pos, Entity viewer) {
        Vec3 center = Vec3.atCenterOf(pos);
        for (Direction face : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
            Vec3 faceCenter = center.add(normal.scale(HALF));
            if (eye.subtract(faceCenter).dot(normal) > 0 && seesFace(level, eye, pos, faceCenter, face, viewer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the eye sees the middle of a living thing's body.
     *
     * @param level  the level
     * @param eye    the eye
     * @param body   the body
     * @param viewer the entity whose eye it is
     * @return true when no block stands between the eye and the body's middle
     */
    public static boolean seesBody(ServerLevel level, Vec3 eye, AABB body, Entity viewer) {
        return level.clip(new ClipContext(eye, body.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                viewer)).getType() == HitResult.Type.MISS;
    }

    /**
     * Whether the eye sees the middle or a corner of one face of a block.
     *
     * @param level      the level
     * @param eye        the eye
     * @param pos        the block
     * @param faceCenter the face's middle
     * @param face       the face
     * @param viewer     the entity whose eye it is
     * @return true when a sight line to the face first meets the block
     */
    private static boolean seesFace(ServerLevel level, Vec3 eye, BlockPos pos, Vec3 faceCenter, Direction face,
                                    Entity viewer) {
        Direction.Axis[] across = acrossAxes(face.getAxis());
        double reach = HALF - CORNER_INSET;
        double[][] offsets = {{0, 0}, {-reach, -reach}, {-reach, reach}, {reach, -reach}, {reach, reach}};
        for (double[] offset : offsets) {
            Vec3 point = faceCenter.add(along(across[0], offset[0])).add(along(across[1], offset[1]));
            if (sightLineMeets(level, eye, point, pos, viewer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param axis a face's axis
     * @return the two axes lying across the face
     */
    private static Direction.Axis[] acrossAxes(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y};
        };
    }

    /**
     * @param axis   an axis
     * @param amount how far along it
     * @return the offset
     */
    private static Vec3 along(Direction.Axis axis, double amount) {
        return new Vec3(axis.choose(amount, 0, 0), axis.choose(0, amount, 0), axis.choose(0, 0, amount));
    }

    /**
     * Whether the sight line from the eye to a point on a block's face first meets that block.
     *
     * @param level  the level
     * @param eye    the eye
     * @param point  the point on the face
     * @param pos    the block
     * @param viewer the entity whose eye it is
     * @return true when nothing stands between
     */
    private static boolean sightLineMeets(ServerLevel level, Vec3 eye, Vec3 point, BlockPos pos, Entity viewer) {
        Vec3 past = point.add(point.subtract(eye).normalize().scale(CORNER_INSET));
        BlockHitResult hit = level.clip(new ClipContext(eye, past, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                viewer));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
