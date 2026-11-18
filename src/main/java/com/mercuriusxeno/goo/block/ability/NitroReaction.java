package com.mercuriusxeno.goo.block.ability;

/**
 * What a Nitro cell does when something acts on it.
 * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
 * vacuum-and-unstable-goo-items-do-not-set-nitro-off
 */
public enum NitroReaction {
    /** The cell is spent and explodes, setting off the Nitro its blast reaches. */
    DETONATE,
    /** The cell stays as it is. */
    HOLD
}
