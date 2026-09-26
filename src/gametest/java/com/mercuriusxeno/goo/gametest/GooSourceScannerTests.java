package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.util.List;

/**
 * Gametests for the glove's goo scan over the inventory: a hub item carrying filled
 * canisters is a goo source (decision diagnose-then-fix-overlay-and-scan).
 */
public final class GooSourceScannerTests {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int FIRST_ROCK = 1_200;
    private static final int SECOND_ROCK = 800;
    private static final int NETHER_AMOUNT = 500;
    private static final int ROCK_TOTAL = FIRST_ROCK + SECOND_ROCK;
    private static final int HUB_INVENTORY_SLOT = 3;
    private static final String AGGREGATE_ROCK = "rock the scan aggregates from the hub";
    private static final String AGGREGATE_NETHER = "nether the scan aggregates from the hub";
    private static final String HAS_ENOUGH = "hasEnough sees every rock mB the hub carries";
    private static final String DEPLETED = "rock mB deplete draws from the hub";
    private static final String HUB_ROCK_LEFT = "rock left in the hub's canisters after depletion";
    private static final String HUB_NETHER_LEFT = "nether left in the hub's canisters after depletion";

    private GooSourceScannerTests() {
    }

    /**
     * A hub item carrying filled canisters answers every scanner entry point: aggregateAvailable
     * counts its goo, hasEnough sees it, and deplete draws from its canisters and writes them back.
     *
     * @param helper the gametest helper
     */
    public static void hubItemIsAGooSource(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hub = new ItemStack(GooItems.HUB.get());
        hub.set(GooDataComponents.HUB_CANISTERS.get(), List.of(
                canisterWith(ROCK, FIRST_ROCK), canisterWith(NETHER, NETHER_AMOUNT), canisterWith(ROCK, SECOND_ROCK)));
        player.getInventory().setItem(HUB_INVENTORY_SLOT, hub);

        var available = GooSourceScanner.aggregateAvailable(player);
        helper.assertValueEqual(available.getOrDefault(ROCK, 0), ROCK_TOTAL, AGGREGATE_ROCK);
        helper.assertValueEqual(available.getOrDefault(NETHER, 0), NETHER_AMOUNT, AGGREGATE_NETHER);
        helper.assertTrue(GooSourceScanner.hasEnough(player, ROCK, ROCK_TOTAL), HAS_ENOUGH);

        helper.assertValueEqual(GooSourceScanner.deplete(player, ROCK, ROCK_TOTAL), ROCK_TOTAL, DEPLETED);
        ItemStack held = player.getInventory().getItem(HUB_INVENTORY_SLOT);
        helper.assertValueEqual(carriedVolume(held, ROCK), 0, HUB_ROCK_LEFT);
        helper.assertValueEqual(carriedVolume(held, NETHER), NETHER_AMOUNT, HUB_NETHER_LEFT);
        helper.succeed();
    }

    private static ItemStack canisterWith(ResourceKey<GooTypeDefinition> type, int amount) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(canister, type, amount);
        return canister;
    }

    private static int carriedVolume(ItemStack hub, ResourceKey<GooTypeDefinition> type) {
        return hub.getOrDefault(GooDataComponents.HUB_CANISTERS.get(), List.<ItemStack>of()).stream()
                .map(CanisterItem::getFluidContent)
                .filter(content -> type.equals(content.getGooType()))
                .mapToInt(content -> content.amount())
                .sum();
    }
}
