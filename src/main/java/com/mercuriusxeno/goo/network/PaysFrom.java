package com.mercuriusxeno.goo.network;

/**
 * Where a held spray pays for what it lays: the glove's from the goo the inventory carries, the
 * sprayer's from its fitted canister alone.
 * decision spray-item-sprays-residuals-beside-the-glove
 * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
 */
public enum PaysFrom {
    /** The goo the caster's inventory carries, as the glove pays. */
    INVENTORY,
    /** The canister fitted in the sprayer the caster holds. */
    SPRAYER_CANISTER
}
