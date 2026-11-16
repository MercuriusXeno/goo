package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the roll a spirits' bite makes on the Leech cell under the bitten
 * thing, at the deplete chance hex_leech.json ships.
 * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
 */
class LeechSpiritsTest {

    private static final int FIVE_LAYERS = 5;
    private static final double CHANCE = LeechGooStep.in(AbilityJson.decode("hex_leech").behaviors())
            .orElseThrow().depleteChance();

    @Test
    void aDrawUnderTheChanceTakesALayer() {
        assertEquals(FIVE_LAYERS - 1, LeechSpirits.layersAfterBite(FIVE_LAYERS, CHANCE / 2, CHANCE));
    }

    @Test
    void aDrawOverTheChanceLeavesTheCellWhole() {
        assertEquals(FIVE_LAYERS, LeechSpirits.layersAfterBite(FIVE_LAYERS, (1 + CHANCE) / 2, CHANCE));
    }

    @Test
    void aBiteTakingTheLastLayerLeavesNone() {
        assertEquals(0, LeechSpirits.layersAfterBite(1, CHANCE / 2, CHANCE));
    }
}
