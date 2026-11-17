package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Molten cell spends one layer melting the block below it, however many it
 * holds, and none where the block melts into nothing
 * (decisions molten-melts-only-obsidian-magma-and-iron-core-one-layer-each,
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block).
 */
class MoltenMeltTest {

    @Test
    void aCellSpendsOneLayerOnlyOverAMeltingBlock() {
        assertEquals(List.of(1, 1, 0), List.of(MoltenMelt.layersSpent(1, true), MoltenMelt.layersSpent(15, true),
                MoltenMelt.layersSpent(15, false)));
    }
}
