package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a held Bore breaks this tick: the block at its point and its
 * surroundings. The point is where the eye line meets the first breakable
 * block within the reach, short of the first solid block outside the tag,
 * and the pick is that block and every breakable block within the point's
 * radius of the point, so aiming at a face's middle picks one block, at an
 * edge two and at a corner four, and none deeper. Once they break the next
 * tick's point lands on the block behind, so the point moves deeper while
 * the hold runs.
 * decision bore-breaks-the-block-at-the-point-and-its-surroundings
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
 *
 * @param blocks the breakable blocks at the point, the aimed block first; empty where the eye line meets none
 * @param tunnel every cell the eye line crosses from the eye out to the point, air among them, where Bore
 *               strikes what stands
 */
record BorePick(List<BlockPos> blocks, List<BlockPos> tunnel) {

    /**
     * Picks what a bore breaks this tick.
     *
     * @param eye         the eye the line runs from
     * @param pointRadius how far round the point the pick reaches; 0 picks the aimed block alone
     * @param aim         the stream's aim: its reach along the look
     * @param air         whether a cell holds air
     * @param breakable   whether a cell holds a block in the tag
     * @return the pick
     */
    static BorePick of(Vec3 eye, double pointRadius, ChannelAim aim, Predicate<BlockPos> air,
                       Predicate<BlockPos> breakable) {
        List<BlockPos> crossed = BoreStep.blocksAlong(eye, aim.aimPoint());
        int met = firstMet(crossed, air, breakable);
        Optional<BorePoint> point = pointAt(crossed, met, eye, aim.aimPoint(), breakable);
        if (point.isEmpty()) {
            return new BorePick(List.of(), List.copyOf(crossed.subList(1, met)));
        }
        BlockPos aimed = point.get().block();
        Predicate<BlockPos> surrounding = cell -> !cell.equals(aimed) && !cell.equals(crossed.getFirst())
                && breakable.test(cell);
        return new BorePick(blocksAt(point.get().at(), pointRadius, aimed, surrounding),
                List.copyOf(crossed.subList(1, met + 1)));
    }

    /**
     * Where a bore's point stands along an eye line.
     *
     * @param eye       the eye the line runs from
     * @param reachEnd  the end of the reach along the look
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the point, empty where the line meets no breakable block within the reach
     */
    static Optional<BorePoint> pointOf(Vec3 eye, Vec3 reachEnd, Predicate<BlockPos> air,
                                       Predicate<BlockPos> breakable) {
        List<BlockPos> crossed = BoreStep.blocksAlong(eye, reachEnd);
        return pointAt(crossed, firstMet(crossed, air, breakable), eye, reachEnd, breakable);
    }

    private static Optional<BorePoint> pointAt(List<BlockPos> crossed, int met, Vec3 eye, Vec3 reachEnd,
                                               Predicate<BlockPos> breakable) {
        if (met == crossed.size() || !breakable.test(crossed.get(met))) {
            return Optional.empty();
        }
        BlockPos aimed = crossed.get(met);
        return Optional.of(new BorePoint(aimed, new AABB(aimed).clip(eye, reachEnd).orElse(Vec3.atCenterOf(aimed))));
    }

    /**
     * The aimed block and every block round it the point reaches.
     *
     * @param point       the point
     * @param pointRadius how far round the point the pick reaches
     * @param aimed       the block the point lands on
     * @param surrounding whether a cell other than the aimed block may join the pick
     * @return the blocks, the aimed block first
     */
    private static List<BlockPos> blocksAt(Vec3 point, double pointRadius, BlockPos aimed,
                                           Predicate<BlockPos> surrounding) {
        List<BlockPos> blocks = new ArrayList<>(List.of(aimed));
        for (BlockPos cell : cellsAround(point, pointRadius)) {
            if (surrounding.test(cell) && reaches(point, pointRadius, cell)) {
                blocks.add(cell.immutable());
            }
        }
        return List.copyOf(blocks);
    }

    /**
     * Where the eye line first meets a block past the eye's own: a breakable
     * block, which it aims at, or a solid block outside the tag, which stops it.
     *
     * @param crossed   the blocks the eye line crosses, the eye's own first
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return that block's index on the line, or the line's length where it meets none
     */
    private static int firstMet(List<BlockPos> crossed, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        for (int i = 1; i < crossed.size(); i++) {
            if (breakable.test(crossed.get(i)) || !air.test(crossed.get(i))) {
                return i;
            }
        }
        return crossed.size();
    }

    /**
     * Every cell a ball about the point could touch.
     *
     * @param point  the ball's middle
     * @param radius the ball's radius
     * @return the cells
     */
    private static Iterable<BlockPos> cellsAround(Vec3 point, double radius) {
        return BlockPos.betweenClosed(BlockPos.containing(point.subtract(radius, radius, radius)),
                BlockPos.containing(point.add(radius, radius, radius)));
    }

    /**
     * Whether a cell lies within the point's radius of the point: nearer than
     * the radius at its nearest, so a ball whose edge only meets a face misses
     * the block.
     *
     * @param point  the point
     * @param radius the point's radius
     * @param cell   the cell
     * @return true for a cell the point reaches
     */
    private static boolean reaches(Vec3 point, double radius, BlockPos cell) {
        return new AABB(cell).distanceToSqr(point) < radius * radius;
    }
}
