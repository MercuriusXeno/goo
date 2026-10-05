package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the affordability gate a glove press passes before any swing, packet
 * or sound: the cost at the aimed marker's stack position against the
 * player's holdings (decisions unaffordable-click-does-nothing, flat-cost-per-throw).
 */
class GloveThrowSenderTest {

    private static ClientAbility clientAbility(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(), definition.cost(), definition.delivery(),
                definition.badge());
    }

    private static boolean affordsWithHoldings(ClientAbility ability, int holdings) {
        return GloveThrowSender.affordsThrow(ability, amount -> holdings >= amount);
    }

    @Test
    void holdingsOneShortOfTheCostRefuseTheThrow() {
        ClientAbility mine = clientAbility("unstable_proximity_mine");

        assertFalse(affordsWithHoldings(mine, mine.cost() - 1));
    }

    @Test
    void holdingsCoveringTheCostAllowTheThrow() {
        ClientAbility mine = clientAbility("unstable_proximity_mine");

        assertTrue(affordsWithHoldings(mine, mine.cost()));
    }

    @Test
    void unsyncedAbilityPricesAtTheServerFallback() {
        assertFalse(GloveThrowSender.affordsThrow(null, amount -> GooThrowHandler.THROW_COST - 1 >= amount));
        assertTrue(GloveThrowSender.affordsThrow(null, amount -> GooThrowHandler.THROW_COST >= amount));
    }
}
