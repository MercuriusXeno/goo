package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The face of blocks Unmake's soup drinks: the square about the aimed block
 * on the face under the cursor, one deep, the outer ring first and the aimed
 * block last, so the cursor rests on the face until the square is gone and a
 * hold kept still then reaches the face behind it.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class SiphonFace {

    private SiphonFace() {
    }

    /**
     * The side of a block the cursor rests on: the aim point lies on a face,
     * which stands farthest from the block's middle along its own axis.
     *
     * @param aimPoint the cursor's point
     * @param aimed    the block it rests on
     * @return the face
     */
    public static Direction faceOf(Vec3 aimPoint, BlockPos aimed) {
        Vec3 out = aimPoint.subtract(Vec3.atCenterOf(aimed));
        return Direction.getApproximateNearest(out.x, out.y, out.z);
    }

    /**
     * The square about a block in the plane of a face.
     *
     * @param aimed  the block the cursor rests on
     * @param axis   the face's axis, which the square lies square to
     * @param radius how far the square reaches from the aimed block
     * @return the blocks, the outer ring first, its corners before its sides, and the aimed block last
     */
    public static List<BlockPos> square(BlockPos aimed, Direction.Axis axis, int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                offsets.add(new int[] {a, b});
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(at -> -Math.max(Math.abs(at[0]), Math.abs(at[1])))
                .thenComparingInt(at -> -(Math.abs(at[0]) + Math.abs(at[1]))));
        List<BlockPos> square = new ArrayList<>();
        for (int[] at : offsets) {
            square.add(inPlane(aimed, axis, at[0], at[1]));
        }
        return square;
    }

    /**
     * A block in the plane square to an axis, offset from another.
     *
     * @param aimed the block offset from
     * @param axis  the axis the plane lies square to
     * @param a     the offset along the plane's first direction
     * @param b     the offset along its second
     * @return the block
     */
    private static BlockPos inPlane(BlockPos aimed, Direction.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> aimed.offset(0, b, a);
            case Y -> aimed.offset(a, 0, b);
            case Z -> aimed.offset(a, b, 0);
        };
    }
}
