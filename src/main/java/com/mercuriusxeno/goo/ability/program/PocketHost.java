package com.mercuriusxeno.goo.ability.program;

/**
 * A host over a player whose Pocket can open.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public interface PocketHost extends StepHost {

    /**
     * Opens the player's pocket as a container.
     *
     * @param slotCost the void each slot the stored items fill reserves
     */
    void openPocket(int slotCost);
}
