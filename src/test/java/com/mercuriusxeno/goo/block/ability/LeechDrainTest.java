package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.block.ability.LeechDrain.DrainSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the choice of the life a hit drains to its source through Leech
 * goo, the damage dealt for a direct living source striking a victim in the
 * goo and nothing otherwise, and the roll a drained strike makes on the cell
 * under its victim at the deplete chance hex_leech.json ships.
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
 */
class LeechDrainTest {

    private static final float DAMAGE = 4f;
    private static final LeechGooStep SHIPPED = LeechGooStep.in(AbilityJson.decode("hex_leech").behaviors())
            .orElseThrow();

    @Nested
    class DrainedLife {

        @Test
        void aLivingSourceStrikingAVictimInTheGooDrainsTheDamage() {
            assertEquals(DAMAGE, LeechDrain.drainedLife(DAMAGE, true, DrainSource.DIRECT_LIVING));
        }

        @Test
        void theSameHitOnAVictimOutsideTheGooDrainsNothing() {
            assertEquals(0f, LeechDrain.drainedLife(DAMAGE, false, DrainSource.DIRECT_LIVING));
        }

        @Test
        void sourcelessDamageToAVictimInTheGooDrainsNothing() {
            assertEquals(0f, LeechDrain.drainedLife(DAMAGE, true, DrainSource.NONE));
        }

        @Test
        void anIndirectHitOnAVictimInTheGooDrainsNothing() {
            assertEquals(0f, LeechDrain.drainedLife(DAMAGE, true, DrainSource.INDIRECT));
        }
    }

    @Nested
    class Deplete {

        private static final int FIVE_LAYERS = 5;

        @Test
        void aDrawUnderTheChanceTakesALayer() {
            double chance = SHIPPED.depleteChance();
            assertEquals(FIVE_LAYERS - 1, LeechDrain.layersAfterStrike(FIVE_LAYERS, chance / 2, chance));
        }

        @Test
        void aDrawOverTheChanceLeavesTheCellWhole() {
            double chance = SHIPPED.depleteChance();
            assertEquals(FIVE_LAYERS, LeechDrain.layersAfterStrike(FIVE_LAYERS, (1 + chance) / 2, chance));
        }

        @Test
        void aStrikeTakingTheLastLayerLeavesNone() {
            double chance = SHIPPED.depleteChance();
            assertEquals(0, LeechDrain.layersAfterStrike(1, chance / 2, chance));
        }
    }
}
