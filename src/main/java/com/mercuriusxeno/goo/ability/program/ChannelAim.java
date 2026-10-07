package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a channeled ability's hold points this tick: the point the client's
 * cursor stands on, and the plane fixed at the height the player stood at
 * when the hold began. A stream's block pass aims at the end of its reach
 * along the look and holds no plane.
 * decision flatten-disc-cursor-breaks-above-the-plane
 * decision bore-vortex-with-a-worldspace-shake
 *
 * @param aimPoint the world point under the client's cursor, or a stream's reach along the look
 * @param planeY   the top of the block the cursor rested on when the hold began, negative infinity for a stream
 * @param coneDegrees a stream's cone, apex to rim, in degrees; zero for a channel aiming one point
 */
public record ChannelAim(Vec3 aimPoint, double planeY, double coneDegrees) {

    /**
     * The aim of a channel pointing at one point, with no cone.
     *
     * @param aimPoint the world point under the client's cursor
     * @param planeY   the top of the block the cursor rested on when the hold began
     */
    public ChannelAim(Vec3 aimPoint, double planeY) {
        this(aimPoint, planeY, 0);
    }

    /** How far past the aim point, along the line from the eye, the aimed block is read. */
    private static final double INTO_THE_FACE = 0.01;
    /** How far the cursor's area reaches from the aimed block each way: one, for a 3x3. */
    private static final int AREA_REACH = 1;
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
     * The plane a hold remembers from the block the cursor rests on as it
     * begins: that block's top, so the block stays and what stands above it
     * breaks (decision flatten-disc-cursor-breaks-above-the-plane).
     *
     * @param cursorBlockY the y of the block the cursor rests on
     * @return the plane's height
     */
    public static double planeAbove(int cursorBlockY) {
        return cursorBlockY + 1.0;
    }

    /**
     * The 3x3 area where the cursor is: the aimed block and its eight
     * neighbors in the plane of the face the cursor rests on
     * (decision flatten-disc-cursor-breaks-above-the-plane).
     *
     * @param aimed the aimed block
     * @return the nine blocks
     */
    public List<BlockPos> areaAround(BlockPos aimed) {
        Direction.Axis normal = faceAxis();
        List<BlockPos> area = new ArrayList<>();
        for (int first = -AREA_REACH; first <= AREA_REACH; first++) {
            for (int second = -AREA_REACH; second <= AREA_REACH; second++) {
                area.add(switch (normal) {
                    case X -> aimed.offset(0, first, second);
                    case Y -> aimed.offset(first, 0, second);
                    case Z -> aimed.offset(first, second, 0);
                });
            }
        }
        return area;
    }

    /**
     * The axis square to the face the cursor rests on: the aim point lies on
     * a block face, so the coordinate nearest a whole number names it.
     *
     * @return the face's normal axis
     */
    Direction.Axis faceAxis() {
        double offX = offWhole(aimPoint.x);
        double offY = offWhole(aimPoint.y);
        double offZ = offWhole(aimPoint.z);
        if (offY <= offX && offY <= offZ) {
            return Direction.Axis.Y;
        }
        return offX <= offZ ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static double offWhole(double coordinate) {
        return Math.abs(coordinate - Math.rint(coordinate));
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
