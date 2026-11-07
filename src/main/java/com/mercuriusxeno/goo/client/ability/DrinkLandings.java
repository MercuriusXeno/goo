package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.Set;

/**
 * When an Unmake drink's blocks begin to peel into chunks: each landing of a
 * square on a block once, the first frame it has landed, the whole block
 * standing as chunks from then until its last chunk leaves; a block the
 * client first sees already drained peels not at all.
 * decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand
 */
public final class DrinkLandings {

    private final Set<Landing> landed = new HashSet<>();

    /**
     * One landing of a square on a block: a block placed back where a drained
     * one stood and drunk in the same drink lands again later, and peels again.
     *
     * @param pos   where the block stands
     * @param start the game time its square lands
     */
    private record Landing(BlockPos pos, long start) {
    }

    /**
     * @param block a block of the drink
     * @param now   the game time, with the partial tick
     * @return whether the block begins to peel this frame: the first frame at or past its start while it still
     *         drains
     */
    public boolean peelsAt(DrinkTree.Block block, double now) {
        return now >= block.start() && now < block.end()
                && landed.add(new Landing(block.pos().immutable(), block.start()));
    }
}
