package com.mercuriusxeno.goo.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * When each block of a shattering hunk crumbles: one after another from the
 * blocks leading into what it struck, each a set span after the last, so the
 * hunk comes apart block by block instead of vanishing at once. The server
 * drops each block as its crumble ends and the client draws the crumble, both
 * from this one schedule.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class HunkShatter {

    private HunkShatter() {
    }

    /**
     * The face a hunk struck with: the axis direction nearest its motion.
     *
     * @param motion the motion it struck with
     * @return the leading face, down for a hunk at rest
     */
    public static Direction leadingFace(Vec3 motion) {
        return motion.lengthSqr() == 0 ? Direction.DOWN
                : Direction.getApproximateNearest(motion.x, motion.y, motion.z);
    }

    /**
     * Each block's turn to crumble: the blocks furthest along the leading face first.
     *
     * @param blocks  the hunk's blocks
     * @param leading the face it struck with
     * @return by block index, its turn, 0 first
     */
    public static int[] turnsOf(List<BlockHunk.HunkBlock> blocks, Direction leading) {
        int[] order = IntStream.range(0, blocks.size()).boxed()
                .sorted(Comparator.comparingInt((Integer index) -> -lead(blocks.get(index), leading))
                        .thenComparingInt(index -> index))
                .mapToInt(Integer::intValue).toArray();
        int[] turns = new int[blocks.size()];
        for (int turn = 0; turn < order.length; turn++) {
            turns[order[turn]] = turn;
        }
        return turns;
    }

    private static int lead(BlockHunk.HunkBlock block, Direction leading) {
        return block.offset().getX() * leading.getStepX() + block.offset().getY() * leading.getStepY()
                + block.offset().getZ() * leading.getStepZ();
    }

    /**
     * @param shatterStart the game time the hunk struck
     * @param turn         a block's turn
     * @return the game time that block begins to crumble
     */
    public static long crumbleStart(long shatterStart, int turn) {
        return shatterStart + (long) turn * BlockHunk.BLOCK_STAGGER_TICKS;
    }

    /**
     * @param shatterStart the game time the hunk struck
     * @param turn         a block's turn
     * @return the game time that block has crumbled away
     */
    public static long crumbleEnd(long shatterStart, int turn) {
        return crumbleStart(shatterStart, turn) + BlockHunk.CRUMBLE_TICKS;
    }
}
