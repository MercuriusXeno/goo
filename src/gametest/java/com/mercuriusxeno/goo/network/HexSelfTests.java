package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.RandomEnchantment;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for hex's self abilities, invoked from the glove the way a
 * real invocation reaches the server.
 */
public final class HexSelfTests {

    private static final Identifier HEX_ENCHANT = Identifier.parse("goo:hex_enchant");
    private static final String ABILITY_REQUIRED = "The hex enchant ability should be loaded";
    private static final String SHOULD_TAKE_THE_BOOK = "Enchant should take the one book, %d remain";
    private static final String SHOULD_GIVE_ONE_BOOK = "Enchant should give one enchanted book, %d given";
    private static final String SHOULD_HOLD_ONE_LEVEL_ONE =
            "The enchanted book should hold one enchantment at level one: %s";

    private HexSelfTests() {
    }

    /**
     * A player holding one book invokes Enchant: the book is gone and one
     * enchanted book stands in its place, holding a single enchantment at
     * level one (decision enchant-book-with-a-purple-afterimage).
     *
     * @param helper the gametest helper
     */
    public static void enchantGivesOneLevelOneBook(GameTestHelper helper) {
        AbilityDefinition enchant = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_ENCHANT);
        helper.assertTrue(enchant != null, ABILITY_REQUIRED);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.HEX, HEX_ENCHANT);
        KnownRecipes.teachRequires(player, enchant);
        player.getInventory().add(new ItemStack(Items.BOOK));

        SelfDeliveryTests.invoke(player, GooTypes.HEX, HEX_ENCHANT);

        int books = stacksOf(player.getInventory(), Items.BOOK).size();
        List<ItemStack> enchanted = stacksOf(player.getInventory(), Items.ENCHANTED_BOOK);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(books == 0, String.format(SHOULD_TAKE_THE_BOOK, books));
        helper.assertTrue(enchanted.size() == 1, String.format(SHOULD_GIVE_ONE_BOOK, enchanted.size()));
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(enchanted.getFirst());
        helper.assertTrue(stored.size() == 1 && stored.entrySet().stream()
                        .allMatch(entry -> entry.getIntValue() == RandomEnchantment.LEVEL),
                String.format(SHOULD_HOLD_ONE_LEVEL_ONE, stored));
        helper.succeed();
    }

    private static List<ItemStack> stacksOf(Inventory inventory, Item item) {
        List<ItemStack> found = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                found.add(stack);
            }
        }
        return found;
    }
}
