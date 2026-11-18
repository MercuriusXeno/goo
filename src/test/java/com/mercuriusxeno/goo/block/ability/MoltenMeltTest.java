package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Molten cell spends one layer on each melt or cook step of a block it
 * touches, as many as its layers pay for, the block below first, and none
 * where no block melts or cooks
 * (decisions molten-melts-only-obsidian-magma-and-iron-core-one-layer-each,
 * molten-cooks-the-block-below-it-as-flames-cooks-blocks,
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block,
 * molten-heats-every-block-its-cell-touches).
 */
class MoltenMeltTest {

    @Test
    void aCellPaysOneLayerForEachBlockThatCooksOrMelts() {
        assertEquals(List.of(List.of("below"), List.of("below", "wall"), List.of()),
                List.of(MoltenMelt.paidFor(1, List.of("below")), MoltenMelt.paidFor(15, List.of("below", "wall")),
                        MoltenMelt.paidFor(15, List.<String>of())));
    }

    /**
     * A cell short on layers finishes the blocks its layers pay for, the block below first.
     * molten-heats-every-block-its-cell-touches
     */
    @Test
    void aCellShortOnLayersFinishesTheBlockBelowFirst() {
        assertEquals(List.of("below"), MoltenMelt.paidFor(1, List.of("below", "wall")));
    }
}
