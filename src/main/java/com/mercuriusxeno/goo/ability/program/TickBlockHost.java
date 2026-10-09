package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import java.util.Optional;

/**
 * A host that can tick a block faster: it names the block it reaches and
 * runs that block's ticker extra times.
 * tick-channel-marches-squares-on-the-face
 */
public interface TickBlockHost extends StepHost {

    /**
     * The block this host ticks: the block a held stream ends on.
     *
     * @return the block, empty where the host reaches none
     */
    Optional<BlockPos> tickedBlock();

    /**
     * Runs a block's block entity ticker extra times this tick; a block with
     * no ticking block entity is left alone.
     *
     * @param pos   the block
     * @param times how many extra ticks it takes
     */
    void tickBlock(BlockPos pos, int times);
}
