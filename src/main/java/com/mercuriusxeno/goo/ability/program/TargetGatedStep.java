package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * A step that refuses a throw before it is paid for when the face it
 * strikes cannot take it, so the throw costs nothing.
 * hole-closes-the-space-to-an-opening-ahead
 */
public interface TargetGatedStep extends Step {

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
