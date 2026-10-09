package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.RandomEnchantment;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
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

    private static final Identifier HEX_FUSE = Identifier.parse("goo:hex_fuse");
    private static final String FUSE_REQUIRED = "The hex fuse ability should be loaded";
    private static final String SHOULD_FUSE_TO_ONE = "Fuse should leave one enchanted book of the pair, %d stand";
    private static final String SHOULD_RAISE_SHARPNESS = "The fused book should hold Sharpness II alone: %s";
    private static final String SHOULD_DRAIN_COST = "Fuse should drain its cost of %d, drained %d";
    private static final String SHOULD_DRAIN_NOTHING = "A refused Fuse should drain nothing, drained %d";
    private static final String SHOULD_KEEP_BOOKS = "A refused Fuse should leave both books, %d stand";
    private static final int SHARPNESS_TWO = 2;

    private HexSelfTests() {
    }

    /**
     * A player carrying two Sharpness I books invokes Fuse: one Sharpness II
     * book stands in their place and Fuse's cost drains
     * (decision fuse-two-books-for-hex-goo).
     *
     * @param helper the gametest helper
     */
    public static void fuseTwoSharpnessOne(GameTestHelper helper) {
        AbilityDefinition fuse = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_FUSE);
        helper.assertTrue(fuse != null, FUSE_REQUIRED);
        ServerPlayer player = fuser(helper, fuse);
        Holder<Enchantment> sharpness = sharpness(helper);
        player.getInventory().add(sharpnessOne(sharpness));
        player.getInventory().add(sharpnessOne(sharpness));
        int heldBefore = heldHex(player);

        SelfDeliveryTests.invoke(player, GooTypes.HEX, HEX_FUSE);

        int drained = heldBefore - heldHex(player);
        List<ItemStack> books = stacksOf(player.getInventory(), Items.ENCHANTED_BOOK);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(books.size() == 1, String.format(SHOULD_FUSE_TO_ONE, books.size()));
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(books.getFirst());
        helper.assertTrue(stored.size() == 1 && stored.getLevel(sharpness) == SHARPNESS_TWO,
                String.format(SHOULD_RAISE_SHARPNESS, stored));
        helper.assertTrue(drained == fuse.cost(), String.format(SHOULD_DRAIN_COST, fuse.cost(), drained));
        helper.succeed();
    }

    /**
     * A player carrying one Sharpness I book and no pair invokes Fuse: it is
     * refused before the cost, so no goo drains and the book stands
     * (decision fuse-two-books-for-hex-goo).
     *
     * @param helper the gametest helper
     */
    public static void fuseWithoutPairCostsNothing(GameTestHelper helper) {
        AbilityDefinition fuse = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_FUSE);
        helper.assertTrue(fuse != null, FUSE_REQUIRED);
        ServerPlayer player = fuser(helper, fuse);
        player.getInventory().add(sharpnessOne(sharpness(helper)));
        int heldBefore = heldHex(player);

        SelfDeliveryTests.invoke(player, GooTypes.HEX, HEX_FUSE);

        int drained = heldBefore - heldHex(player);
        int books = stacksOf(player.getInventory(), Items.ENCHANTED_BOOK).size();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
        helper.assertTrue(books == 1, String.format(SHOULD_KEEP_BOOKS, books));
        helper.succeed();
    }

    private static ServerPlayer fuser(GameTestHelper helper, AbilityDefinition fuse) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.HEX, HEX_FUSE);
        KnownRecipes.teachRequires(player, fuse);
        return player;
    }

    private static Holder<Enchantment> sharpness(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
    }

    private static ItemStack sharpnessOne(Holder<Enchantment> sharpness) {
        return EnchantmentHelper.createBook(new EnchantmentInstance(sharpness, 1));
    }

    private static int heldHex(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.HEX, 0);
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
