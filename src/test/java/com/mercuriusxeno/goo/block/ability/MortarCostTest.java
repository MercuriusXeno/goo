package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what a Mortar cell spends: one layer for each block it hardens and
 * one for each second a mob stands in it, with no chance in it.
 * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
 */
class MortarCostTest {

    private static final int FIVE_LAYERS = 5;

    @Test
    void aHardenedBlockAndASecondOfAMobStandingTakeALayerEach() {
        assertEquals(FIVE_LAYERS - 2, MortarCellBlock.layersAfterCosts(FIVE_LAYERS, 1, 1));
    }

    @Test
    void neitherLeavesTheCellWhole() {
        assertEquals(FIVE_LAYERS, MortarCellBlock.layersAfterCosts(FIVE_LAYERS, 0, 0));
    }

    @Test
    void costsPastTheLastLayerLeaveNone() {
        assertEquals(0, MortarCellBlock.layersAfterCosts(1, 2, 1));
    }
}
