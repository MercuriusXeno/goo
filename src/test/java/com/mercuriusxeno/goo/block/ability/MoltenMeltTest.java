package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Molten cell spends three layers melting the block below it where it
 * holds three or more and the block melts into something, and none otherwise
 * (decisions molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava,
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block).
 */
class MoltenMeltTest {

    @Test
    void aCellSpendsThreeLayersOnlyOverAMeltingBlockWhereItHoldsThree() {
        assertEquals(List.of(3, 3, 0, 0), List.of(MoltenMelt.layersSpent(3, true), MoltenMelt.layersSpent(15, true),
                MoltenMelt.layersSpent(2, true), MoltenMelt.layersSpent(15, false)));
    }
}
