package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the affordability gate a glove press passes before any swing, packet
 * or sound: the cost at the aimed marker's stack position against the
 * player's holdings (decisions unaffordable-click-does-nothing, flat-cost-per-throw).
 */
class GloveThrowSenderTest {

    private static final int BLINK_TRIP = 8;

    private static ClientAbility clientAbility(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(), definition.cost(), definition.delivery(),
                definition.badge(), definition.requires());
    }

    private static boolean affordsWithHoldings(ClientAbility ability, int holdings) {
        return GloveThrowSender.affordsThrow(ability, amount -> holdings >= amount, item -> true);
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

    // decision ability-json-names-its-reagent
    @Test
    void aMissingReagentRefusesTheThrowTheGooCovers() {
        AbilityDefinition definition = AbilityJson.decode("hex_spawn");
        ClientAbility spawn = new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(), definition.cost(),
                definition.delivery(), definition.badge(), definition.requires(), definition.area(),
                definition.indicator(), definition.consumes());

        assertFalse(GloveThrowSender.affordsThrow(spawn, amount -> true, item -> false));
        assertTrue(GloveThrowSender.affordsThrow(spawn, amount -> true, definition.consumes()::contains));
    }

    /**
     * The HUD figure and the affordability gate read one price: the JSON's
     * flat cost plus its per-block amount for the trip plus its wall
     * surcharge (decision blink-lands-safely-costed-by-distance).
     */
    @Test
    void blinkPricesItsTripAsItsJsonNames() {
        AbilityDefinition definition = AbilityJson.decode("ender_blink");
        ClientAbility blink = new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(), definition.cost(),
                definition.delivery(), definition.badge(), definition.requires(), definition.area(),
                definition.indicator(), definition.consumes(), definition.upkeep(), definition.distancePrice());
        Optional<BlinkLanding> trip = Optional.of(new BlinkLanding(Vec3.ZERO, BLINK_TRIP, true));
        int priced = definition.cost() + BLINK_TRIP * definition.distancePrice().perBlock()
                + definition.distancePrice().wallCost();

        assertTrue(definition.distancePrice().perBlock() > 0 && definition.distancePrice().wallCost() > 0);
        assertEquals(priced, GloveThrowSender.throwCostOf(blink, trip));
        assertEquals(GooFormat.formatAmount(priced), blink.costLabel(trip));
        assertFalse(GloveThrowSender.affordsThrow(blink, trip, amount -> priced - 1 >= amount, item -> true));
        assertTrue(GloveThrowSender.affordsThrow(blink, trip, amount -> priced >= amount, item -> true));
    }

    @Test
    void unsyncedAbilityPricesAtTheServerFallback() {
        assertFalse(GloveThrowSender.affordsThrow(null, amount -> GooThrowHandler.THROW_COST - 1 >= amount, item -> true));
        assertTrue(GloveThrowSender.affordsThrow(null, amount -> GooThrowHandler.THROW_COST >= amount, item -> true));
    }
}
