package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a channeled ability's hold points this tick: the point the client's
 * cursor stands on, and the face the cursor rested on when the hold began,
 * fixed for the hold. A stream's block pass aims at the end of its reach
 * along the look and holds no face.
 * decision flatten-disc-cursor-breaks-above-the-plane
 * decision bore-vortex-with-a-worldspace-shake
 *
 * @param aimPoint    the world point under the client's cursor, or a stream's reach along the look
 * @param plane       the face the hold began on, or null where it began on none or the hold is a stream
 * @param coneDegrees a stream's cone, apex to rim, in degrees; zero for a channel aiming one point
 * @param heldTicks   the hold's age this tick, 1 on the tick it began
 */
public record ChannelAim(Vec3 aimPoint, @Nullable FacePlane plane, double coneDegrees, int heldTicks) {

    /** The age a hold reads when none was counted: its first tick. */
    public static final int FIRST_TICK = 1;

    /** How far past the aim point, along the line from the eye, the aimed block is read. */
    private static final double INTO_THE_FACE = 0.01;
    /** How many blocks out from the face the swath reaches. */
    private static final int SWATH_DEPTH = 3;
    /** A slice's ring, in turn around the middle, then the middle: ring in. */
    private static final int[][] RING_IN = {
        {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {0, 0}
    };

    /**
     * The face a hold began on: a block and the side of it the cursor rested
     * on, whose plane the hold works out from, on the side the face looks
     * out to and never behind it
     * (decision flatten-disc-cursor-breaks-above-the-plane).
     *
     * @param block the block the cursor rested on
     * @param face  the side of it the cursor rested on
     */
    public record FacePlane(BlockPos block, Direction face) {

        /**
         * The cell just out from the face in line with a block, where the
         * cursor's disc lies flat on the plane.
         *
         * @param aimed the block the cursor rests on now
         * @return the cell in front of the plane, in line with the aimed block
         */
        public BlockPos cellOutFrom(BlockPos aimed) {
            return layer(aimed, 1);
        }

        /**
         * A block in line with another, a number of layers out from the face.
         *
         * @param aimed the block whose in-plane position the result keeps
         * @param out   how many layers out from the face, 1 the layer touching it
         * @return the block
         */
        BlockPos layer(BlockPos aimed, int out) {
            int along = block.get(face.getAxis()) + out * face.getAxisDirection().getStep();
            return switch (face.getAxis()) {
                case X -> new BlockPos(along, aimed.getY(), aimed.getZ());
                case Y -> new BlockPos(aimed.getX(), along, aimed.getZ());
                case Z -> new BlockPos(aimed.getX(), aimed.getY(), along);
            };
        }
    }

    /**
     * The aim of a channel pointing at one point.
     *
     * @param aimPoint the world point under the client's cursor
     * @param plane    the face the hold began on, or null where it began on none
     */
    public ChannelAim(Vec3 aimPoint, @Nullable FacePlane plane) {
        this(aimPoint, plane, 0, FIRST_TICK);
    }

    /**
     * The aim of a stream's block pass, at the end of its reach.
     *
     * @param aimPoint    the end of the stream's reach along the look
     * @param plane       null, a stream holding no face
     * @param coneDegrees the stream's cone, apex to rim, in degrees
     */
    public ChannelAim(Vec3 aimPoint, @Nullable FacePlane plane, double coneDegrees) {
        this(aimPoint, plane, coneDegrees, FIRST_TICK);
    }

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
     * Flatten's swath where the cursor is, in breaking order: the 3x3 in line
     * with the aimed block, from the layer touching the face out to 3 layers,
     * the outermost first, all on the side the face looks out to
     * (decision flatten-disc-cursor-breaks-above-the-plane).
     *
     * @param aimed the aimed block
     * @return the 27 blocks, outermost first; none where the hold began on no face
     */
    public List<BlockPos> swathOutermostFirst(BlockPos aimed) {
        List<BlockPos> swath = new ArrayList<>();
        if (plane == null) {
            return swath;
        }
        for (int out = SWATH_DEPTH; out >= 1; out--) {
            swath.addAll(sliceRingIn(plane.layer(aimed, out), plane.face().getAxis()));
        }
        return swath;
    }

    /**
     * A 3x3 slice square to an axis, ring in: the eight around the middle in
     * turn, then the middle; Flatten lays each layer of its swath with it
     * (decision flatten-disc-cursor-breaks-above-the-plane).
     *
     * @param middle the slice's middle
     * @param main   the axis the slice stands square to
     * @return the nine blocks in breaking order
     */
    static List<BlockPos> sliceRingIn(BlockPos middle, Direction.Axis main) {
        List<BlockPos> slice = new ArrayList<>();
        for (int[] at : RING_IN) {
            slice.add(switch (main) {
                case X -> middle.offset(0, at[1], at[0]);
                case Y -> middle.offset(at[0], 0, at[1]);
                case Z -> middle.offset(at[0], at[1], 0);
            });
        }
        return slice;
    }
}
