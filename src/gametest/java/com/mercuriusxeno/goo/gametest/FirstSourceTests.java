package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Gametest theory for the crosshair panel's source: over inventories mixing
 * blobs, omniblobs, canisters (a hub's included) and vats in main slots and
 * the offhand, GooSourceScanner.firstSource names the stack a deplete of one
 * mB shrinks (decision crosshair-panel-shows-source-and-cost).
 */
public final class FirstSourceTests {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int OFFHAND = Inventory.SLOT_OFFHAND;
    private static final int VOLUME = 3_000;
    private static final int ONE_MB = 1;
    private static final int BLOB_COUNT = 2;
    private static final int NO_SLOT = -1;
    private static final int SLOT_0 = 0;
    private static final int SLOT_1 = 1;
    private static final int SLOT_2 = 2;
    private static final int SLOT_3 = 3;
    private static final int SLOT_4 = 4;
    private static final int SLOT_5 = 5;
    private static final int SLOT_7 = 7;
    private static final int SLOT_9 = 9;
    private static final int SLOT_30 = 30;
    private static final String NO_SOURCE = "Inventory %d: firstSource named no stack";
    private static final String NOT_SHRUNK = "Inventory %d: the deplete shrank slot %d, not firstSource's slot %d";
    private static final String NOTHING_SHRUNK = "Inventory %d: a deplete of one mB shrank no rock source";

    private FirstSourceTests() {
    }

    private static Supplier<ItemStack> blob(ResourceKey<GooTypeDefinition> type) {
        return () -> BlobStacks.createForOutput(type, BLOB_COUNT * BlobStacks.MB_PER_BLOB);
    }

    private static Supplier<ItemStack> omniblob(ResourceKey<GooTypeDefinition> type) {
        return () -> GooOmniblobItem.createWithVolume(type, VOLUME);
    }

    private static Supplier<ItemStack> canister(ResourceKey<GooTypeDefinition> type) {
        return () -> {
            ItemStack canister = new ItemStack(GooItems.CANISTER.get());
            CanisterItem.addGoo(canister, type, VOLUME);
            return canister;
        };
    }

    private static Supplier<ItemStack> hub(ResourceKey<GooTypeDefinition> type) {
        return () -> {
            ItemStack hub = new ItemStack(GooItems.HUB.get());
            hub.set(GooDataComponents.HUB_CANISTERS.get(), List.of(canister(type).get()));
            return hub;
        };
    }

    private static Supplier<ItemStack> vat(ResourceKey<GooTypeDefinition> type) {
        return () -> {
            ItemStack vat = new ItemStack(GooItems.VAT.get());
            VatBlockItem.addGoo(vat, type, VOLUME);
            return vat;
        };
    }

    /**
     * The inventories the theory runs over.
     *
     * @return each inventory's filled slots and what each holds
     */
    private static List<Map<Integer, Supplier<ItemStack>>> inventories() {
        return List.of(
                Map.of(SLOT_0, canister(ROCK), SLOT_5, blob(ROCK)),
                Map.of(SLOT_0, vat(ROCK), SLOT_9, omniblob(ROCK)),
                Map.of(SLOT_2, canister(ROCK), SLOT_1, vat(ROCK)),
                Map.of(OFFHAND, blob(ROCK), SLOT_30, blob(ROCK)),
                Map.of(OFFHAND, vat(ROCK), SLOT_0, canister(NETHER)),
                Map.of(SLOT_0, omniblob(NETHER), SLOT_1, canister(ROCK), SLOT_3, blob(ROCK)),
                Map.of(SLOT_4, hub(ROCK), SLOT_2, vat(ROCK)),
                Map.of(SLOT_7, canister(ROCK), SLOT_3, canister(ROCK), OFFHAND, omniblob(ROCK)));
    }

    /**
     * For every inventory, firstSource names the stack a deplete of one mB shrinks.
     *
     * @param helper the gametest helper
     */
    public static void firstSourceIsTheStackDepleteShrinks(GameTestHelper helper) {
        List<Map<Integer, Supplier<ItemStack>>> inventories = inventories();
        for (int index = 0; index < inventories.size(); index++) {
            assertFirstSourceShrinks(helper, index, inventories.get(index));
        }
        helper.succeed();
    }

    private static void assertFirstSourceShrinks(GameTestHelper helper, int index,
                                                 Map<Integer, Supplier<ItemStack>> layout) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        layout.forEach((slot, stack) -> inventory.setItem(slot, stack.get()));
        List<Integer> slots = new ArrayList<>(layout.keySet());
        List<Integer> before = slots.stream().map(slot -> GooSourceScanner.volumeIn(inventory.getItem(slot), ROCK)).toList();

        ItemStack first = GooSourceScanner.firstSource(player, ROCK);
        helper.assertFalse(first.isEmpty(), String.format(NO_SOURCE, index));
        int firstSlot = slotHolding(inventory, slots, first);
        GooSourceScanner.deplete(player, ROCK, ONE_MB);

        int shrunk = NO_SLOT;
        for (int i = 0; i < slots.size(); i++) {
            if (GooSourceScanner.volumeIn(inventory.getItem(slots.get(i)), ROCK) < before.get(i)) {
                shrunk = slots.get(i);
                break;
            }
        }
        helper.assertTrue(shrunk >= 0, String.format(NOTHING_SHRUNK, index));
        helper.assertTrue(shrunk == firstSlot, String.format(NOT_SHRUNK, index, shrunk, firstSlot));
    }

    private static int slotHolding(Inventory inventory, List<Integer> slots, ItemStack stack) {
        return slots.stream().filter(slot -> inventory.getItem(slot) == stack).findFirst().orElse(NO_SLOT);
    }
}
