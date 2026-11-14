package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a held Bore breaks this tick: whatever its drill's cross section
 * intersects. The cross section is the ellipse the drill cone cuts in the
 * plane square to the look ({@link BoreDrillShape}), centered on the eye line
 * where it meets the first breakable block within the reach, short of the
 * first solid block outside the tag, so aiming at a face's middle picks one
 * block, near an edge two and near a corner four, never every block the
 * drill touches. The block behind each picked block progresses at half speed
 * out of view, so once the pick breaks the next tick's cross section lands on
 * blocks already cracking and moves deeper smoothly while the hold runs.
 * decision bore-breaks-what-the-drill-cross-section-intersects
 * decision bore-breaks-the-block-at-the-point-and-its-surroundings
 * decision bore-block-behind-progresses-at-half-speed
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
 *
 * @param blocks the breakable blocks the cross section intersects, the aimed block first; empty where the eye
 *               line meets none
 * @param behind the breakable block behind each picked block, where the eye line runs on past the aimed one
 * @param tunnel every cell the eye line crosses from the eye out to the cross section, air among them, where
 *               Bore strikes what stands
 */
record BorePick(List<BlockPos> blocks, List<BlockPos> behind, List<BlockPos> tunnel) {

    /**
     * Picks what a bore breaks this tick.
     *
     * @param eye        the eye the line runs from
     * @param shape      the drill cone whose cross section the pick reads
     * @param gloveRight whether the glove is in the right hand, the side the cross section's long axis lies
     * @param aim        the stream's aim: its reach along the look
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block in the tag
     * @return the pick
     */
    static BorePick of(Vec3 eye, BoreDrillShape shape, boolean gloveRight, ChannelAim aim, Predicate<BlockPos> air,
                       Predicate<BlockPos> breakable) {
        List<BlockPos> crossed = BoreStep.blocksAlong(eye, aim.aimPoint());
        int met = firstMet(crossed, air, breakable);
        Optional<BorePoint> point = pointAt(crossed, met, eye, aim.aimPoint(), breakable);
        if (point.isEmpty()) {
            return new BorePick(List.of(), List.of(), List.copyOf(crossed.subList(1, met)));
        }
        BlockPos aimed = point.get().block();
        Predicate<BlockPos> surrounding = cell -> !cell.equals(aimed) && !cell.equals(crossed.getFirst())
                && breakable.test(cell);
        Vec3 look = aim.aimPoint().subtract(eye).normalize();
        List<BlockPos> blocks = new ArrayList<>(List.of(aimed));
        shape.cellsCrossedAt(point.get().at(), look, gloveRight).stream().filter(surrounding).forEach(blocks::add);
        List<BlockPos> behind = met + 1 < crossed.size()
                ? behindEach(blocks, crossed.get(met + 1).subtract(aimed), breakable) : List.of();
        return new BorePick(List.copyOf(blocks), behind, List.copyOf(crossed.subList(1, met + 1)));
    }

    /**
     * The breakable block behind each picked block: the cell at the offset
     * from the aimed block to the next cell the eye line enters past it.
     *
     * @param blocks    the picked blocks
     * @param onward    the offset from the aimed block to the next cell on the eye line
     * @param breakable whether a cell holds a block in the tag
     * @return the blocks behind, none of them picked
     */
    private static List<BlockPos> behindEach(List<BlockPos> blocks, BlockPos onward, Predicate<BlockPos> breakable) {
        return blocks.stream().map(block -> block.offset(onward)).distinct()
                .filter(cell -> !blocks.contains(cell) && breakable.test(cell)).toList();
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

    /**
     * Where an eye line stops: where it enters the first block past the eye's
     * own that is not air, breakable or not, so a drill drawn to it never runs
     * into a block it cannot break.
     *
     * @param eye       the eye the line runs from
     * @param reachEnd  the end of the reach along the look
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return where the line enters that block, or the end of the reach where it meets none
     */
    static Vec3 stopOf(Vec3 eye, Vec3 reachEnd, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        List<BlockPos> crossed = BoreStep.blocksAlong(eye, reachEnd);
        int met = firstMet(crossed, air, breakable);
        return met == crossed.size() ? reachEnd
                : new AABB(crossed.get(met)).clip(eye, reachEnd).orElse(Vec3.atCenterOf(crossed.get(met)));
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
}
