package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Breaks blocks over several ticks for one hold: each tick a step hands it
 * the blocks it is breaking, each with the share of a break it gains that
 * tick, and it adds that share to the block's progress, draws the vanilla
 * block break texture at the stage the progress reached, and breaks the block
 * once its progress fills. A block the step stops handing it loses its
 * progress and its cracks. Every ability that breaks over time breaks
 * through this one piece.
 * decision gradual-breaks-share-one-break-texture-piece
 * decision bore-drills-the-eye-line-with-steady-progress
 */
public final class GradualBreak {

    /** The crack stage that clears a block's break texture. */
    public static final int CLEARED = -1;
    /** Vanilla's crack stages, 0 to 9. */
    private static final int STAGES = 10;
    private static final double FULL = 1;
    /** Shares summed tick by tick land a hair under a whole break, as five ticks of a fifth do. */
    private static final double SUM_SLACK = 1e-9;

    private final Map<BlockPos, Progress> progress = new HashMap<>();

    /**
     * Progresses every block a step is breaking this tick, and clears every
     * block it was breaking that the step no longer hands it.
     *
     * @param rates each block the step is breaking with the share of a break it gains this tick
     * @param host  the host that cracks and breaks the blocks
     */
    public void progress(Map<BlockPos, Double> rates, BlockBreakHost host) {
        dropUnfed(rates.keySet(), host);
        rates.forEach((pos, rate) -> advance(pos, rate, host));
    }

    private void dropUnfed(Set<BlockPos> fed, BlockBreakHost host) {
        Iterator<BlockPos> held = progress.keySet().iterator();
        while (held.hasNext()) {
            BlockPos pos = held.next();
            if (!fed.contains(pos)) {
                held.remove();
                host.crackBlock(pos, CLEARED);
            }
        }
    }

    private void advance(BlockPos pos, double rate, BlockBreakHost host) {
        Progress before = progress.getOrDefault(pos, Progress.NONE);
        double share = before.share() + rate;
        if (share >= FULL - SUM_SLACK) {
            progress.remove(pos);
            host.crackBlock(pos, CLEARED);
            host.breakBlock(pos);
            return;
        }
        int stage = stageOf(share);
        if (stage != before.stage()) {
            host.crackBlock(pos, stage);
        }
        progress.put(pos.immutable(), new Progress(share, stage));
    }

    /**
     * The crack stage a share of a break draws.
     *
     * @param share the share, 0 to 1
     * @return the stage, 0 to 9
     */
    private static int stageOf(double share) {
        return Math.min(STAGES - 1, (int) (share * STAGES));
    }

    /**
     * A block's progress toward broken and the crack stage last drawn on it.
     *
     * @param share the share of a break gained
     * @param stage the stage drawn, {@link #CLEARED} before any
     */
    private record Progress(double share, int stage) {
        static final Progress NONE = new Progress(0, CLEARED);
    }
}
