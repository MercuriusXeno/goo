package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A step that refuses a throw before it is paid for when the target cannot
 * take it, so the cast fizzles and costs nothing; Meteo refuses a target
 * with no clear path to the sky.
 * decision meteo-needs-a-clear-sky
 */
public interface TargetGatedStep extends Step {

    /**
     * Whether a throw may land in a cell.
     *
     * @param level the level thrown in
     * @param cell  the cell the blob would land in
     * @return true when the cell can take the throw
     */
    boolean admitsTarget(ServerLevel level, BlockPos cell);
}
