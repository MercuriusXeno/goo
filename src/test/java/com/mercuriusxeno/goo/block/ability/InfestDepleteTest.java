package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.InfestStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the roll each gnaw and bite of an Infest cell makes, at the chance
 * zombie_infest.json ships: a draw under the chance takes a layer, a draw
 * over it leaves the cell whole.
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 */
class InfestDepleteTest {

    private static final int FIVE_LAYERS = 5;

    private static double shippedChance() {
        return InfestStep.in(AbilityJson.decode("zombie_infest").behaviors()).orElseThrow().depleteChance();
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

    @Test
    void aLastLayerRolledAwayLeavesNone() {
        assertEquals(0, LayerDeplete.layersAfterRoll(1, 0, shippedChance()));
    }
}
