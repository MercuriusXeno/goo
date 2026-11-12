package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A flat wall facing north across the world at one z, the block a test's sight
 * line meets, so where a line meets it depends only on where the line starts
 * and the way it runs.
 */
final class FlatWall {

    /** The z the wall's north face stands at. */
    static final double FACE_Z = 10;

    private FlatWall() {
    }

    /**
     * Clips a line against the wall.
     *
     * @param from the line's start, north of the wall
     * @param to   the line's end
     * @return the hit on the wall's face, a miss at the end where the line falls short
     */
    static BlockHitResult clip(Vec3 from, Vec3 to) {
        double share = (FACE_Z - from.z) / (to.z - from.z);
        if (to.z <= from.z || share > 1) {
            return BlockHitResult.miss(to, Direction.NORTH, BlockPos.containing(to));
        }
        Vec3 at = from.add(to.subtract(from).scale(share));
        return new BlockHitResult(at, Direction.NORTH, BlockPos.containing(at.add(0, 0, 0.5)), false);
    }

    /**
     * Where a line stops against the wall: the face it meets, or its end.
     *
     * @param from the line's start
     * @param to   the line's end
     * @return the stop
     */
    static Vec3 stop(Vec3 from, Vec3 to) {
        return clip(from, to).getLocation();
    }

    /**
     * Where a line from a point along a look meets the wall.
     *
     * @param from the line's start
     * @param look the unit look, running south
     * @return the point on the face
     */
    static Vec3 metAlong(Vec3 from, Vec3 look) {
        return from.add(look.scale((FACE_Z - from.z) / look.z));
    }
}
