package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * The world as a blink reads it: whether a body fits in a box, and the first
 * block face a line crosses. The server's level and the client's level both
 * answer it, so the teleport and the cursor resolve one landing.
 * Decision blink-lands-safely-costed-by-distance.
 */
public interface BlinkSpace {

    /**
     * Whether a box holds no solid block.
     *
     * @param box the box a body would fill
     * @return true when the body fits there
     */
    boolean fits(AABB box);

    /**
     * The first block face a line crosses from one point to another.
     *
     * @param from where the line starts
     * @param to   where the line ends
     * @return the face crossed, empty when the line crosses none
     */
    Optional<FaceHit> firstFace(Vec3 from, Vec3 to);

    /**
     * Where a line first crossed a block face.
     *
     * @param point the point on the face
     * @param block the block the face belongs to
     * @param face  the side of the block crossed
     */
    record FaceHit(Vec3 point, BlockPos block, Direction face) {
    }
}
