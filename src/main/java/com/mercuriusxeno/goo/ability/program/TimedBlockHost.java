package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.block.Block;

/**
 * A host that can stand a block at its cell for a set time, after which the
 * block's own scheduled tick removes it.
 * weird-bounces-and-softens-harm
 */
public interface TimedBlockHost extends StepHost {

    /**
     * Writes a block at the host's cell and schedules its tick after the
     * given time.
     *
     * @param block the block to stand
     * @param ticks the ticks until the block's scheduled tick
     */
    void placeForTicks(Block block, int ticks);
}
