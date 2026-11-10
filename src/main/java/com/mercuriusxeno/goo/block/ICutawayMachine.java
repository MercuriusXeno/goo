package com.mercuriusxeno.goo.block;

import net.minecraft.world.phys.BlockHitResult;

/**
 * A machine with a cutaway region whose click reaches the machine rather than
 * the block face, so a held item's placement stands down there (decision
 * hosts-answer-bounds-through-interfaces).
 */
@FunctionalInterface
public interface ICutawayMachine {

    /**
     * @param hit the ray trace hit on this machine
     * @return true when the hit lands in the machine's cutaway
     */
    boolean isCutawayHit(BlockHitResult hit);

    /**
     * Whether a hit is a click the machine's cutaway takes as its own, so a held canister's
     * placement stands down there.
     *
     * @param hit the ray trace hit on this machine
     * @return true for a hit in a cutaway that takes clicks
     */
    default boolean takesCutawayClick(BlockHitResult hit) {
        return isCutawayHit(hit);
    }
}
