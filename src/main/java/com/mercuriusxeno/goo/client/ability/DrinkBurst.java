package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.Set;

/**
 * When an Unmake drink's blocks burst into break particles: each landing of a
 * square on a block once, whole, the first frame it has landed, so all of it is in the air at
 * once, a cloud at the block draining into its stream; a block the client
 * first sees already drained bursts not at all.
 * decision unmake-drink-is-a-stream-of-break-particles
 */
public final class DrinkBurst {

    /** The particles a block of a whole block's goo bursts into. */
    static final int PER_BLOCK = 48;
    /** The fewest particles any block bursts into, so a block of little goo still shows a stream. */
    static final int FEWEST = 4;

    private final Set<Landing> burst = new HashSet<>();

    /**
     * One landing of a square on a block: a block placed back where a drained
     * one stood and drunk in the same drink lands again later, and bursts again.
     *
     * @param pos   where the block stands
     * @param start the game time its square lands
     */
    private record Landing(BlockPos pos, long start) {
    }

    /**
     * @param block a block of the drink
     * @param now   the game time, with the partial tick
     * @return how many particles the block bursts into this frame: its whole count the first frame at or past its
     *         start while it still drains, none otherwise
     */
    public int burstAt(DrinkTree.Block block, double now) {
        if (now < block.start() || now >= block.end() || !burst.add(new Landing(block.pos().immutable(), block.start()))) {
            return 0;
        }
        return countOf(block.scale());
    }

    /**
     * @param scale a block's stream scale, the square root of its goo volume over a whole block's
     * @return the particles it bursts into, in proportion to its volume
     */
    static int countOf(double scale) {
        return Math.max(FEWEST, (int) Math.round(PER_BLOCK * scale * scale));
    }
}
