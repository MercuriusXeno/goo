package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A step that refuses a throw before it is paid for when the block it
 * strikes cannot take it, so the throw costs nothing; Melt strikes only
 * meltable stone.
 * melt-blob-absorbs-into-lava
 */
public interface TargetGatedStep extends Step {

    /**
     * Whether a throw may strike a block: the aim refuses any other before
     * anything is paid, the client sending no throw and the server dropping
     * one sent anyway; Melt strikes only meltable stone.
     * melt-blob-absorbs-into-lava
     *
     * @param struck the block the throw would strike
     * @return true when the block can take the throw
     */
    default boolean admitsStruck(BlockState struck) {
        return true;
    }
}
