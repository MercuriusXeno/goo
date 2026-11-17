package com.mercuriusxeno.goo.ability.typhoon;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Typhoon's world goo ships as typhoon_updraft.json, a residual stream
 * spray, and the column a cell of it lifts through stands a block over its
 * top for each of its layers.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-wears-the-residual-badge
 * updraft-goo-lifts-one-block-per-layer
 */
class UpdraftAbilityTest {

    private static final String UPDRAFT = "typhoon_updraft";

    @Test
    void updraftWearsTheResidualBadgeAndSpraysAsAStream() {
        AbilityDefinition updraft = AbilityJson.decode(UPDRAFT);

        assertEquals(AbilityBadge.RESIDUAL, updraft.badge());
        assertEquals(DeliveryKind.STREAM, updraft.delivery().kind());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 15})
    void aCellsColumnStandsABlockOverItsTopForEachLayer(int layers) {
        assertEquals(layers, UpdraftStep.heightOver(layers));
    }
}
