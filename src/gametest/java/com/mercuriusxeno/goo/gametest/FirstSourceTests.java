package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for the crosshair panel's source: over a real goo stack, canister, hub and vat,
 * GooSourceScanner.firstSource names the stack a deplete of one mB shrinks (decision crosshair-panel-shows-source-and-cost).
 */
@RegistersGameTests
public final class FirstSourceTests {
    private static final String GLOVE_FIRST_SOURCE_DEPLETES_FIRST = "glove_first_source_depletes_first";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GLOVE_FIRST_SOURCE_DEPLETES_FIRST, FirstSourceTests::firstSourceIsTheStackDepleteShrinks);
    }


    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int VOLUME = 3_000;
    private static final int ONE_MB = 1;
    private static final int NO_SLOT = -1;
    private static final int SLOT_0 = 0;
    private static final int SLOT_2 = 2;
    private static final int SLOT_4 = 4;
    private static final int SLOT_9 = 9;
    private static final String NO_SOURCE = "Inventory %d: firstSource named no stack";
    private static final String NOT_SHRUNK = "Inventory %d: the deplete shrank slot %d, not firstSource's slot %d";
    private static final String NOTHING_SHRUNK = "Inventory %d: a deplete of one mB shrank no rock source";

    private FirstSourceTests() {
    }

    private static Supplier<ItemStack> goo(ResourceKey<GooTypeDefinition> type) {
        return () -> GooItem.createWithVolume(type, VOLUME);
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
     * One inventory per real carrier kind, so each kind's gooVolume and drawGoo are read on a real stack; the walk
     * order across slots and passes stands in GooSourceScannerTest.
     *
     * @return each inventory's filled slots and what each holds
     */
    private static List<Map<Integer, Supplier<ItemStack>>> inventories() {
        return List.of(
                Map.of(SLOT_9, goo(ROCK)),
                Map.of(SLOT_2, canister(ROCK)),
                Map.of(SLOT_4, hub(ROCK)),
                Map.of(SLOT_0, vat(ROCK)));
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
