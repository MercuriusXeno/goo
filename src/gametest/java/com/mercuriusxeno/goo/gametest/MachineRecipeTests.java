package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametests for the machine crafting recipes: each resolves a shaped grid
 * through the level's recipe access, the path a crafting table takes.
 */
@RegistersGameTests
public final class MachineRecipeTests {
    private static final String CRYSTALLIZER_CRAFTED_FROM_GOLD_AND_NETHERITE = "machine_recipe_crystallizer_crafted_from_gold_and_netherite";
    private static final String CRYSTALLIZER_DERIVES_A_VALUE = "machine_recipe_crystallizer_derives_a_value";

    private static final int GRID_SIDE = 3;

    private static final String GOLD_GRID_NOT_CRAFTED = "Gold, comparator, netherite scrap and an amethyst block should craft one crystallizer";
    private static final String COPPER_GRID_CRAFTED = "The crystallizer grid with copper in place of gold should craft no crystallizer";
    private static final String CRYSTALLIZER_DENIED = "The crystallizer should not be denied a goo value";
    private static final String CRYSTALLIZER_UNVALUED = "The crystallizer should derive a goo value from its recipe";

    private MachineRecipeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, CRYSTALLIZER_CRAFTED_FROM_GOLD_AND_NETHERITE,
                MachineRecipeTests::crystallizerCraftedFromGoldAndNetherite);
        GooTestFunctions.registerTest(registrar, CRYSTALLIZER_DERIVES_A_VALUE, MachineRecipeTests::crystallizerDerivesAValue);
    }

    /**
     * Gold ingot, comparator, gold ingot over netherite scrap, amethyst block,
     * netherite scrap over three gold ingots crafts one crystallizer; the same
     * grid in copper crafts none.
     * decision crystallizer-crafts-gold-around-amethyst
     *
     * @param helper the gametest helper
     */
    public static void crystallizerCraftedFromGoldAndNetherite(GameTestHelper helper) {
        String[] pattern = {"MRM", "NAN", "MMM"};
        Map<Character, Item> rest = Map.of('R', Items.COMPARATOR, 'N', Items.NETHERITE_SCRAP, 'A', Items.AMETHYST_BLOCK);
        ItemStack crystallizer = craft(helper, grid(pattern, withMetal(rest, Items.GOLD_INGOT)));
        helper.assertTrue(crystallizer.is(GooItems.CRYSTALLIZER.get()) && crystallizer.getCount() == 1, GOLD_GRID_NOT_CRAFTED);
        helper.assertTrue(!craft(helper, grid(pattern, withMetal(rest, Items.COPPER_INGOT))).is(GooItems.CRYSTALLIZER.get()),
                COPPER_GRID_CRAFTED);
        helper.succeed();
    }

    /**
     * The value registry derives a goo value for the crystallizer from its
     * recipe, and holds no denial for it.
     * decision crystallizer-crafts-gold-around-amethyst
     *
     * @param helper the gametest helper
     */
    public static void crystallizerDerivesAValue(GameTestHelper helper) {
        IGooValueLookup values = GooValues.of(helper.getLevel());
        Identifier crystallizerId = BuiltInRegistries.ITEM.getKey(GooItems.CRYSTALLIZER.get());
        helper.assertTrue(!values.isDenied(crystallizerId), CRYSTALLIZER_DENIED);
        GooValue value = values.lookup(crystallizerId);
        helper.assertTrue(value != null && !value.isEmpty(), CRYSTALLIZER_UNVALUED);
        helper.succeed();
    }

    private static Map<Character, Item> withMetal(Map<Character, Item> rest, Item metal) {
        Map<Character, Item> key = new HashMap<>(rest);
        key.put('M', metal);
        return key;
    }

    private static List<ItemStack> grid(String[] pattern, Map<Character, Item> key) {
        List<ItemStack> cells = new ArrayList<>(GRID_SIDE * GRID_SIDE);
        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                Item item = key.get(symbol);
                cells.add(item == null ? ItemStack.EMPTY : new ItemStack(item));
            }
        }
        return cells;
    }

    private static ItemStack craft(GameTestHelper helper, List<ItemStack> cells) {
        CraftingInput grid = CraftingInput.of(GRID_SIDE, GRID_SIDE, cells);
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid))
                .orElse(ItemStack.EMPTY);
    }
}
