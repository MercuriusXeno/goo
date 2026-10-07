package com.mercuriusxeno.goo.ability.program;

/**
 * A host a tap's drip lands on: it counts the drips the block below the tap
 * has taken and grows dripstone down from it
 * (decision petrify-drip-calcifies-and-grows-dripstone).
 */
public interface DripHost extends BlockBreakHost {

    /**
     * Counts this drip on the landing block.
     *
     * @return the drips the block has taken since its count last started over, this one included
     */
    int countDrip();

    /** Starts the landing block's drip count over. */
    void resetDrips();

    /**
     * Grows pointed dripstone one block down from the landing block: a tip in
     * the open cell under the landing block or under the stalactite already
     * hanging from it.
     */
    void growStalactite();
}
