package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.pocket.PocketEntry;
import com.mercuriusxeno.goo.ability.pocket.PocketMenu;
import com.mercuriusxeno.goo.ability.pocket.PocketReserve;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for Void's Pocket: invoked from the glove it opens the pocket's
 * container; a stack shift-clicked in reserves void goo on the vat scale and
 * taken back releases it, and the pocket keeps more entries than any chest
 * holds slots, standing again when the pocket is opened anew
 * (decision pocket-is-a-void-reserving-container-with-scroll-and-search).
 */
@RegistersGameTests
public final class PocketTests {
    private static final String POCKET_STORES_AND_RETURNS = "pocket_stores_and_returns_through_its_screen";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, POCKET_STORES_AND_RETURNS, PocketTests::pocketStoresAndReturns);
    }

    private static final Identifier POCKET = Identifier.parse("goo:void_pocket");
    /** The inventory slot each stored stack is laid in before it is shift-clicked into the pocket. */
    private static final int LAID_SLOT = 20;
    private static final int STACK = 64;
    /** More entries than a double chest's 54 slots. */
    private static final int MANY_ENTRIES = 60;
    /** Void enough for every stack the test stores. */
    private static final int VOID_HELD = 200 * GooStacks.THOUSAND;
    private static final String SHOULD_OPEN = "The Pocket should open its container, the player has %s open";
    private static final String SHOULD_STORE = "The pocket should hold %d cobblestone in one entry, holds %s";
    private static final String SHOULD_EMPTY_SLOT = "The shift-clicked slot should stand empty, holds %s";
    private static final String SHOULD_RESERVE = "Storing a stack should reserve %d mB of void, reserved %d";
    private static final String SHOULD_RETURN = "The stack taken back should stand in the inventory, %d cobblestone do";
    private static final String SHOULD_RELEASE = "Taking the stack back should release the %d mB reserved, released %d";
    private static final String SHOULD_HOLD_MANY = "The pocket should hold %d entries, holds %d";
    private static final String SHOULD_KEEP = "Opened anew the pocket should still hold %d entries, holds %d";

    private PocketTests() {
    }

    /**
     * A player invoking Pocket from the glove stands in its container. A
     * stack of cobblestone shift-clicked in leaves its slot empty, stands as
     * one entry and reserves the void the vat scale names; taken back into
     * the inventory it returns whole and releases that void. Sixty differently
     * named papers stored stand as sixty entries, and the pocket opened again
     * still holds them.
     *
     * @param helper the gametest helper
     */
    public static void pocketStoresAndReturns(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, VOID_HELD));
        PocketMenu pocket = open(helper, player);

        int voidBefore = voidHeld(player);
        player.getInventory().setItem(LAID_SLOT, new ItemStack(Items.COBBLESTONE, STACK));
        pocket.quickMoveStack(player, menuSlot(pocket, player.getInventory(), LAID_SLOT));
        int reserved = voidBefore - voidHeld(player);
        long expected = PocketReserve.reserveToStore(0, STACK);
        List<PocketEntry> entries = pocket.entries();
        helper.assertTrue(entries.size() == 1 && entries.getFirst().kind().is(Items.COBBLESTONE)
                && entries.getFirst().count() == STACK, String.format(SHOULD_STORE, STACK, entries));
        helper.assertTrue(player.getInventory().getItem(LAID_SLOT).isEmpty(),
                String.format(SHOULD_EMPTY_SLOT, player.getInventory().getItem(LAID_SLOT)));
        helper.assertTrue(reserved == expected, String.format(SHOULD_RESERVE, expected, reserved));

        int voidStored = voidHeld(player);
        pocket.clickMenuButton(player, PocketMenu.takeButton(0, true));
        int released = voidHeld(player) - voidStored;
        int returned = player.getInventory().countItem(Items.COBBLESTONE);
        helper.assertTrue(returned == STACK, String.format(SHOULD_RETURN, returned));
        helper.assertTrue(released == reserved, String.format(SHOULD_RELEASE, reserved, released));

        for (int paper = 0; paper < MANY_ENTRIES; paper++) {
            ItemStack named = new ItemStack(Items.PAPER);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Note " + paper));
            player.getInventory().setItem(LAID_SLOT, named);
            pocket.quickMoveStack(player, menuSlot(pocket, player.getInventory(), LAID_SLOT));
        }
        helper.assertTrue(pocket.entries().size() == MANY_ENTRIES,
                String.format(SHOULD_HOLD_MANY, MANY_ENTRIES, pocket.entries().size()));
        player.closeContainer();
        PocketMenu reopened = open(helper, player);
        helper.assertTrue(reopened.entries().size() == MANY_ENTRIES,
                String.format(SHOULD_KEEP, MANY_ENTRIES, reopened.entries().size()));
        helper.succeed();
    }

    private static PocketMenu open(GameTestHelper helper, ServerPlayer player) {
        SelfDeliveryTests.invoke(player, GooTypes.VOID, POCKET);
        helper.assertTrue(player.containerMenu instanceof PocketMenu,
                String.format(SHOULD_OPEN, player.containerMenu.getClass().getSimpleName()));
        return (PocketMenu) player.containerMenu;
    }

    private static int menuSlot(PocketMenu pocket, Inventory inventory, int inventorySlot) {
        for (Slot slot : pocket.slots) {
            if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
                return slot.index;
            }
        }
        throw new IllegalStateException("The pocket's menu holds no slot for inventory slot " + inventorySlot);
    }

    private static int voidHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VOID, 0);
    }
}
