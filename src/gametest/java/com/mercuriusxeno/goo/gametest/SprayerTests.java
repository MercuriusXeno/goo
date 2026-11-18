package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the sprayer's recipe: six gold ingots and a lever, three gold across the top, two
 * down the right side and the lever at the bottom middle, craft one sprayer
 * (decision sprayer-crafts-from-six-gold-ingots-and-a-lever).
 */
@RegistersGameTests
public final class SprayerTests {
    private static final String SPRAYER_CRAFTS = "sprayer_crafts_from_six_gold_ingots_and_a_lever";
    private static final int GRID_SIDE = 3;
    private static final String NOT_CRAFTED =
            "Six gold ingots and a lever in the caulk gun shape should craft one sprayer, but crafted ";

    private SprayerTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPRAYER_CRAFTS, SprayerTests::sprayerCraftsFromSixGoldIngotsAndALever);
    }

    /**
     * The recipe manager, handed the sprayer's grid, answers one sprayer.
     *
     * @param helper the gametest helper
     */
    public static void sprayerCraftsFromSixGoldIngotsAndALever(GameTestHelper helper) {
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack lever = new ItemStack(Items.LEVER);
        CraftingInput grid = CraftingInput.of(GRID_SIDE, GRID_SIDE, List.of(
                gold, gold, gold,
                ItemStack.EMPTY, ItemStack.EMPTY, gold,
                ItemStack.EMPTY, lever, gold));
        ServerLevel level = helper.getLevel();
        ItemStack crafted = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(crafted.is(GooItems.SPRAYER.get()) && crafted.getCount() == 1, NOT_CRAFTED + crafted);
        helper.succeed();
    }
}
