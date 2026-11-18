package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.MortarStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the roll each petrifying step and each block hardened by a Mortar
 * cell makes, at the chance rock_mortar.json ships: a draw under the chance
 * takes a layer, a draw over it leaves the cell whole.
 * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
 */
class MortarDepleteTest {

    private static final int FIVE_LAYERS = 5;

    private static double shippedChance() {
        return MortarStep.in(AbilityJson.decode("rock_mortar").behaviors()).orElseThrow().depleteChance();
    }

    @Test
    void aDrawUnderTheChanceTakesALayer() {
        double chance = shippedChance();

        assertEquals(FIVE_LAYERS - 1, LayerDeplete.layersAfterRoll(FIVE_LAYERS, chance / 2, chance));
    }

    @Test
    void aDrawOverTheChanceLeavesTheCellWhole() {
        double chance = shippedChance();

        assertEquals(FIVE_LAYERS, LayerDeplete.layersAfterRoll(FIVE_LAYERS, (1 + chance) / 2, chance));
    }
}
