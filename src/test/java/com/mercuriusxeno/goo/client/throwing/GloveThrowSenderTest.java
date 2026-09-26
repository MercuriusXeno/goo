package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.BlobThrowHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the affordability gate a glove press passes before any swing, packet
 * or sound: the cost at the aimed marker's stack position against the
 * player's holdings (decision unaffordable-click-does-nothing).
 */
class GloveThrowSenderTest {

    private static final int AIMED_STACKS = 2;

    private static ClientAbility clientAbility(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.chain().fuseTicks(),
                definition.chain().maxStacks(), definition.behaviors(), definition.cost());
    }

    private static boolean affordsWithHoldings(ClientAbility ability, int stacks, int holdings) {
        return GloveThrowSender.affordsThrow(ability, stacks, amount -> holdings >= amount);
    }

    @Test
    void holdingsOneShortOfTheAimedCostRefuseTheThrow() {
        ClientAbility mine = clientAbility("unstable_proximity_mine");
        int cost = mine.throwCost(AIMED_STACKS);

        assertFalse(affordsWithHoldings(mine, AIMED_STACKS, cost - 1));
    }

    @Test
    void holdingsCoveringTheAimedCostAllowTheThrow() {
        ClientAbility mine = clientAbility("unstable_proximity_mine");
        int cost = mine.throwCost(AIMED_STACKS);

        assertTrue(affordsWithHoldings(mine, AIMED_STACKS, cost));
    }

    @Test
    void aimedStackPositionPricesAboveTheFirstThrow() {
        ClientAbility mine = clientAbility("unstable_proximity_mine");

        assertTrue(affordsWithHoldings(mine, 0, mine.throwCost(0)));
        assertFalse(affordsWithHoldings(mine, AIMED_STACKS, mine.throwCost(0)));
    }

    @Test
    void unsyncedAbilityPricesAtTheServerFallback() {
        assertFalse(GloveThrowSender.affordsThrow(null, 0, amount -> BlobThrowHandler.THROW_COST - 1 >= amount));
        assertTrue(GloveThrowSender.affordsThrow(null, 0, amount -> BlobThrowHandler.THROW_COST >= amount));
    }
}
