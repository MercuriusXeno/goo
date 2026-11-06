package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * Whether a throw by a player may strike a face: Quantum's hole strikes
     * only a face with an opening ahead within its reach.
     * hole-closes-the-space-to-an-opening-ahead
     *
     * @param thrower the throwing player
     * @param struck  the block the throw would strike
     * @param face    the face it would strike
     * @return true when the face can take the throw
     */
    default boolean admitsFace(ServerPlayer thrower, BlockPos struck, Direction face) {
        return true;
    }

    /**
     * Shows a throw refused at a face, before anything is paid.
     * hole-closes-the-space-to-an-opening-ahead
     *
     * @param thrower the throwing player
     * @param struck  the block the throw would have struck
     * @param face    the face it would have struck
     */
    default void refuseAtFace(ServerPlayer thrower, BlockPos struck, Direction face) {
    }
}
