package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.SpikesStep;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who Spikes goo strikes, when it strikes them again, and what a strike's
 * roll leaves of the cell, each read on the spikes step metal_spikes.json
 * ships.
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * metal-spends-charge-by-chance
 */
class SpikesImpaleTest {

    private static final SpikesStep SHIPPED = SpikesStep.in(AbilityJson.decode("metal_spikes").behaviors())
            .orElseThrow();
    private static final int FIVE_LAYERS = 5;
    /** How far a draw sits from the chance, either side of it. */
    private static final double MARGIN = 0.01;

    @Nested
    class Struck {

        private Player standing(boolean sneaking) {
            Player player = mock(Player.class);
            when(player.isAlive()).thenReturn(true);
            when(player.isShiftKeyDown()).thenReturn(sneaking);
            return player;
        }

        @Test
        void aLivingThingStandingOnTheCellIsStruck() {
            assertTrue(SpikesImpale.strikes(standing(false), SHIPPED));
        }

        @Test
        void theSameThingSneakingIsSpared() {
            assertFalse(SpikesImpale.strikes(standing(true), SHIPPED));
        }
    }

    @Nested
    class Spend {

        @Test
        void aDrawUnderTheChanceSpendsALayer() {
            double chance = SHIPPED.spendChance();
            assertEquals(FIVE_LAYERS - 1, SpikesImpale.layersAfterStrike(FIVE_LAYERS, chance - MARGIN, chance));
        }

        @Test
        void aDrawOverTheChanceLeavesTheCellWhole() {
            double chance = SHIPPED.spendChance();
            assertEquals(FIVE_LAYERS, SpikesImpale.layersAfterStrike(FIVE_LAYERS, chance + MARGIN, chance));
        }
    }

    @Nested
    class Cooldown {

        private static final long NOW = 1000L;
        private static final long NEVER = -1L;

        @Test
        void aThingNeverStruckIsDue() {
            assertTrue(SpikesImpale.due(NOW, NEVER, SHIPPED.cooldown()));
        }

        @Test
        void aThingStruckWithinTheCooldownWaits() {
            assertFalse(SpikesImpale.due(NOW, NOW - SHIPPED.cooldown() + 1, SHIPPED.cooldown()));
        }

        @Test
        void aThingStruckACooldownAgoIsDueAgain() {
            assertTrue(SpikesImpale.due(NOW, NOW - SHIPPED.cooldown(), SHIPPED.cooldown()));
        }
    }
}
