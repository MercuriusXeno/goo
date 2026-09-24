package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.registries.DeferredItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for the exorite-unenchantable decision: no exorite piece and not
 * the exo gauntlet is enchantable at the table, and an anvil refuses an
 * enchanted book on an exorite piece that it applies to a netherite one.
 */
public final class ExoriteEnchantingTests {

    private static final String REMOVAL = "removal";
    private static final int ANVIL_LEFT = 0;
    private static final int ANVIL_RIGHT = 1;
    private static final int ANVIL_RESULT = 2;
    private static final int MENU_ID = 0;

    private static final String ENCHANTABLE = "Should not be enchantable: ";
    private static final String HAS_COMPONENT = "Should carry no ENCHANTABLE component: ";
    private static final String NETHERITE_NOT_ENCHANTABLE = "A netherite pickaxe should stay enchantable";
    private static final String BOOK_APPLIED = "The anvil should refuse an Efficiency book on an exorite pickaxe";
    private static final String BOOK_REFUSED_ON_NETHERITE = "The anvil should apply an Efficiency book to a netherite pickaxe";

    private ExoriteEnchantingTests() {
    }

    /**
     * Each exorite piece and the exo gauntlet read isEnchantable false with
     * no ENCHANTABLE component; a netherite pickaxe reads true.
     *
     * @param helper the gametest helper
     */
    public static void notEnchantable(GameTestHelper helper) {
        List<Item> pieces = new ArrayList<>(GooItems.EXORITE_SET.stream().map(DeferredItem::get).toList());
        pieces.add(GooItems.EXO_GAUNTLET.get());
        for (Item piece : pieces) {
            ItemStack stack = new ItemStack(piece);
            helper.assertTrue(!stack.isEnchantable(), ENCHANTABLE + piece);
            helper.assertTrue(!stack.has(DataComponents.ENCHANTABLE), HAS_COMPONENT + piece);
        }
        helper.assertTrue(new ItemStack(Items.NETHERITE_PICKAXE).isEnchantable(), NETHERITE_NOT_ENCHANTABLE);
        helper.succeed();
    }

    /**
     * For a survival player, an anvil given an exorite pickaxe and an
     * Efficiency book answers an empty result slot; given a netherite pickaxe
     * and the same book, it answers a result.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void anvilRefusesBook(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false; // the anvil lets a creative player combine any book with any item
        Holder<Enchantment> efficiency = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(efficiency, 1));
        helper.assertTrue(anvilResult(player, new ItemStack(GooItems.EXORITE_PICKAXE.get()), book).isEmpty(),
                BOOK_APPLIED);
        helper.assertTrue(!anvilResult(player, new ItemStack(Items.NETHERITE_PICKAXE), book).isEmpty(),
                BOOK_REFUSED_ON_NETHERITE);
        helper.getLevel().getServer().getPlayerList().remove(player);
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
