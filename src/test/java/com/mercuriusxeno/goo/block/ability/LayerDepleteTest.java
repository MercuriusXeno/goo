package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what an Infest cell spends on a tick: one layer for each block
 * stage it gnaws and each bite it lands, with no chance in it.
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 */
class LayerDepleteTest {

    private static final int FIVE_LAYERS = 5;

    @Test
    void twoGnawsAndABiteTakeALayerEach() {
        assertEquals(2, LayerDeplete.layersAfterCosts(FIVE_LAYERS, 2 + 1));
    }

    @Test
    void neitherLeavesTheCellWhole() {
        assertEquals(FIVE_LAYERS, LayerDeplete.layersAfterCosts(FIVE_LAYERS, 0));
    }

    @Test
    void costsPastTheLastLayerLeaveNone() {
        assertEquals(0, LayerDeplete.layersAfterCosts(1, 3));
    }
}
