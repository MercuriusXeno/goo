package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Where a channeled ability's hold points this tick: the point the client's
 * cursor stands on, and the plane fixed at the height the player stood at
 * when the hold began. A stream's block pass aims at the end of its reach
 * along the look and holds no plane.
 * decision flatten-disc-cursor-breaks-above-the-plane
 * decision bore-vortex-with-a-worldspace-shake
 *
 * @param aimPoint the world point under the client's cursor, or a stream's reach along the look
 * @param planeY   the player's feet height when the hold began, negative infinity for a stream
 * @param coneDegrees a stream's cone, apex to rim, in degrees; zero for a channel aiming one point
 */
public record ChannelAim(Vec3 aimPoint, double planeY, double coneDegrees) {

    /**
     * The aim of a channel pointing at one point, with no cone.
     *
     * @param aimPoint the world point under the client's cursor
     * @param planeY   the player's feet height when the hold began
     */
    public ChannelAim(Vec3 aimPoint, double planeY) {
        this(aimPoint, planeY, 0);
    }

    /** How far past the aim point, along the line from the eye, the aimed block is read. */
    private static final double INTO_THE_FACE = 0.01;
    /** Slack under the plane so a player standing on a block's top reads that height exactly. */
    private static final double PLANE_SLACK = 1.0e-6;

    /**
     * The block under the cursor: the aim point lies on a face, so the block
     * is read a hair past it along the line from the eye.
     *
     * @param eye the channeling player's eye
     * @return the aimed block
     */
    public BlockPos aimedBlock(Vec3 eye) {
        Vec3 line = aimPoint.subtract(eye);
        Vec3 into = line.lengthSqr() == 0 ? aimPoint : aimPoint.add(line.normalize().scale(INTO_THE_FACE));
        return BlockPos.containing(into);
    }

    /**
     * Whether a block stands wholly above the plane: its bottom face at or
     * over the plane, so the block the player stood on stays.
     *
     * @param blockY the block's y
     * @return true for a block above the plane
     */
    public boolean abovePlane(int blockY) {
        return blockY >= planeY - PLANE_SLACK;
    }
}
