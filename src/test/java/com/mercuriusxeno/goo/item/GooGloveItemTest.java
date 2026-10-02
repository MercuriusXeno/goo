package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.GooGloveItem.GloveTier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each glove tier spells the attack damage its main-hand melee hit adds:
 * 2 for the glove, 4 for the gauntlet and 6 for the exo gauntlet
 * (decision glove-damage-by-tier). It reads the tier alone, touching no registry.
 */
class GooGloveItemTest {

    @ParameterizedTest
    @CsvSource({"GLOVE, 2", "GAUNTLET, 4", "EXO_GAUNTLET, 6"})
    void eachTierAddsItsAttackDamage(GloveTier tier, double bonus) {
        assertEquals(bonus, tier.attackDamageBonus());
    }
}
