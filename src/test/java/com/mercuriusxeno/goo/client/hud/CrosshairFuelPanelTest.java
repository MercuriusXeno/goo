package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/** Covers the crosshair panel's row: the first source, the mB it holds and the cost at the aimed stack (decision crosshair-panel-shows-source-and-cost). */
class CrosshairFuelPanelTest {

    private static final int CANISTER_VOLUME = 3000;
    private static final int AIMED_STACKS = 2;

    private static ClientAbility proximityMine() {
        AbilityDefinition definition = AbilityJson.decode("unstable_proximity_mine");
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.chain().fuseTicks(),
                definition.chain().maxStacks(), definition.behaviors(), definition.cost());
    }

    @Test
    void rowReadsTheCanisterItsVolumeAndTheCostAtTheAimedStack() {
        ItemStack canister = mock(ItemStack.class);
        int cost = proximityMine().throwCost(AIMED_STACKS);

        CrosshairFuelPanel.FuelRow row = CrosshairFuelPanel.fuelRow(canister, GooTypes.UNSTABLE, CANISTER_VOLUME, cost);

        assertSame(canister, row.source());
        assertEquals(GooTypes.UNSTABLE, row.type());
        assertEquals("3000 mB", row.heldText());
        assertEquals("- 4500 mB", row.costText());
    }

    @Test
    void aimedStackCostsAboveTheFirstThrowForAPowerLaw() {
        ClientAbility mine = proximityMine();

        assertEquals(2000, mine.throwCost(0));
        assertEquals(4500, mine.throwCost(AIMED_STACKS));
    }
}
