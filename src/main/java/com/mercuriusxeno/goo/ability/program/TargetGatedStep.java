package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A step that refuses a throw before it is paid for when the target cannot
 * take it, so the cast fizzles and costs nothing; Meteo refuses a target
 * with no clear path to the sky.
 * decision meteo-needs-a-clear-sky
 */
public interface TargetGatedStep extends Step {

    /**
     * Whether a throw may land in a cell; a step gating no cell admits every one.
     *
     * @param level the level thrown in
     * @param cell  the cell the blob would land in
     * @return true when the cell can take the throw
     */
    default boolean admitsTarget(ServerLevel level, BlockPos cell) {
        return true;
    }

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
