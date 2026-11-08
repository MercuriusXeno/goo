package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the exorite-enchants-above-netherite decision: every exorite
 * piece enchants at value 20, an anvil applies an enchanted book to an exorite
 * piece, and the enchanting table's selection offers one enchantments. The exo
 * gauntlet stays unenchantable.
 */
@RegistersGameTests
public final class ExoriteEnchantingTests {
    private static final String EXORITE_ENCHANTABLE = "exorite_enchantable";
    private static final String EXORITE_ANVIL_APPLIES_BOOK = "exorite_anvil_applies_book";
    private static final String EXORITE_TABLE_ENCHANTS = "exorite_table_enchants";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, EXORITE_ENCHANTABLE, ExoriteEnchantingTests::enchantable);
        GooTestFunctions.registerTest(registrar, EXORITE_ANVIL_APPLIES_BOOK, ExoriteEnchantingTests::anvilAppliesBook);
        GooTestFunctions.registerTest(registrar, EXORITE_TABLE_ENCHANTS, ExoriteEnchantingTests::tableEnchants);
    }

    private static final String REMOVAL = "removal";
    private static final int ANVIL_LEFT = 0;
    private static final int ANVIL_RIGHT = 1;
    private static final int ANVIL_RESULT = 2;
    private static final int MENU_ID = 0;
    private static final int TABLE_LEVEL = 30;
    private static final long TABLE_SEED = 0L;
    private static final int EXORITE_ENCHANTMENT_VALUE = 20;

    private static final String NOT_ENCHANTABLE = "Should be enchantable: ";
    private static final String WRONG_VALUE = "Should carry ENCHANTABLE at value 20: ";
    private static final String GAUNTLET_ENCHANTABLE = "The exo gauntlet should stay unenchantable";
    private static final String BOOK_REFUSED = "The anvil should apply an Efficiency book to an exorite pickaxe";
    private static final String TABLE_OFFERS_NOTHING = "The table's selection at level 30 should offer an exorite pickaxe an enchantment";

    private ExoriteEnchantingTests() {
    }

    /**
     * Each exorite piece reads isEnchantable true and carries ENCHANTABLE at
     * value 20; the exo gauntlet reads isEnchantable false.
     *
     * @param helper the gametest helper
     */
    public static void enchantable(GameTestHelper helper) {
        List<Item> pieces = GooItems.EXORITE_SET.stream().map(DeferredItem::get).map(Item.class::cast).toList();
        for (Item piece : pieces) {
            ItemStack stack = new ItemStack(piece);
            helper.assertTrue(stack.isEnchantable(), NOT_ENCHANTABLE + piece);
            Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
            helper.assertTrue(enchantable != null && enchantable.value() == EXORITE_ENCHANTMENT_VALUE,
                    WRONG_VALUE + piece);
        }
        helper.assertTrue(!new ItemStack(GooItems.EXO_GAUNTLET.get()).isEnchantable(), GAUNTLET_ENCHANTABLE);
        helper.succeed();
    }

    /**
     * For a survival player, an anvil given an exorite pickaxe and an
     * Efficiency book answers a result carrying Efficiency.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void anvilAppliesBook(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false; // the anvil lets a creative player combine any book with any item
        Holder<Enchantment> efficiency = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(efficiency, 1));
        ItemStack result = anvilResult(player, new ItemStack(GooItems.EXORITE_PICKAXE.get()), book);
        ItemEnchantments enchantments = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertTrue(enchantments.getLevel(efficiency) > 0, BOOK_REFUSED);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * The enchanting table's selection path, EnchantmentHelper.selectEnchantment
     * over the in-table enchantments at level 30, answers at least one
     * enchantment for an exorite pickaxe.
     *
     * @param helper the gametest helper
     */
    public static void tableEnchants(GameTestHelper helper) {
        var inTable = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
        List<EnchantmentInstance> offered = EnchantmentHelper.selectEnchantment(RandomSource.create(TABLE_SEED),
                new ItemStack(GooItems.EXORITE_PICKAXE.get()), TABLE_LEVEL, inTable.stream());
        helper.assertTrue(!offered.isEmpty(), TABLE_OFFERS_NOTHING);
        helper.succeed();
    }

    private static ItemStack anvilResult(ServerPlayer player, ItemStack left, ItemStack right) {
        AnvilMenu anvil = new AnvilMenu(MENU_ID, player.getInventory());
        anvil.getSlot(ANVIL_LEFT).set(left);
        anvil.getSlot(ANVIL_RIGHT).set(right.copy());
        anvil.createResult();
        return anvil.getSlot(ANVIL_RESULT).getItem();
    }
}
