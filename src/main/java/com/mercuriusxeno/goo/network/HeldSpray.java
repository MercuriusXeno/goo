package com.mercuriusxeno.goo.network;

/**
 * What a held spray carries beside its ability: where it pays from and how much it throws a tick, the glove's at
 * the inventory and its residual's own rate, the sprayer's at its canister and its wheel's flow.
 * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
 * decision spray-item-sprays-more-or-less-per-tick
 *
 * @param paysFrom where what the spray lays pays from
 * @param flowRate the multiple of the residual's drips a tick the spray throws
 */
public record HeldSpray(PaysFrom paysFrom, double flowRate) {

    /** The glove's spray: the inventory pays and the residual throws its own drips a tick. */
    public static final HeldSpray GLOVE = new HeldSpray(PaysFrom.INVENTORY, 1.0);
}
