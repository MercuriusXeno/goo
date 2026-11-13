package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooLabelItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for the goo label (decision goo-label-is-paper-plus-one-mb-of-goo):
 * paper beside goo crafts a label through the real recipe manager and leaves the
 * goo 1 mB lighter in the grid, and labels stack by type alone.
 */
@RegistersGameTests
public final class GooLabelTests {
    private static final String LABEL_CRAFTS_FROM_PAPER_AND_GOO = "label_crafts_from_paper_and_goo";
    private static final String GOO_LABELS_STACK_BY_TYPE = "goo_labels_stack_by_type";

    private static final String REMOVAL = "removal";
    private static final int GRID_SIDE = 3;
    private static final int PAPER_CELL = 0;
    private static final int GOO_CELL = 4;
    private static final int GOO_VOLUME = 250;

    private GooLabelTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LABEL_CRAFTS_FROM_PAPER_AND_GOO, GooLabelTests::craftsFromPaperAndGoo);
        GooTestFunctions.registerTest(registrar, GOO_LABELS_STACK_BY_TYPE, GooLabelTests::stackByType);
    }

    /**
     * Paper beside blaze goo at 250 mB crafts one blaze label and leaves the goo at
     * 249 mB in its cell; GooLabelCraftTest holds the 1 mB, 0 mB and label-back grids.
     *
     * @param helper the gametest helper
     */
    public static void craftsFromPaperAndGoo(GameTestHelper helper) {
        List<ItemStack> cells = grid(GooItem.createWithVolume(GooTypes.BLAZE, GOO_VOLUME));
        CraftingInput input = CraftingInput.of(GRID_SIDE, GRID_SIDE, cells);
        RecipeHolder<CraftingRecipe> recipe = recipeFor(helper, input)
                .orElseThrow(() -> helper.assertionException("Paper beside goo should match a recipe"));
        ItemStack label = recipe.value().assemble(input);
        helper.assertTrue(label.getItem() instanceof GooLabelItem && label.getCount() == 1
                && GooLabelItem.typeOf(label) == GooTypes.BLAZE, "The craft should yield one blaze label: " + label);
        NonNullList<ItemStack> remainders = recipe.value().getRemainingItems(input);
        ItemStack goo = remainders.get(gooCellOf(input));
        helper.assertTrue(GooStacks.keyOf(goo) == GooTypes.BLAZE && GooStacks.volumeOf(goo) == GOO_VOLUME - 1,
                "The goo should stay in the grid 1 mB lighter: " + goo);
        helper.succeed();
    }

    /**
     * Two blaze labels merge to one stack of two in an inventory and a frost label
     * stands apart.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void stackByType(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        inventory.add(GooItems.GOO_LABEL.get().labelOf(GooTypes.BLAZE));
        inventory.add(GooItems.GOO_LABEL.get().labelOf(GooTypes.BLAZE));
        inventory.add(GooItems.GOO_LABEL.get().labelOf(GooTypes.FROST));
        List<ItemStack> held = inventory.getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
        helper.assertTrue(held.size() == 2, "Blaze and frost labels should stand in two stacks: " + held);
        helper.assertTrue(held.stream().anyMatch(stack -> GooLabelItem.typeOf(stack) == GooTypes.BLAZE
                && stack.getCount() == 2), "Two blaze labels should merge to one stack of two: " + held);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static List<ItemStack> grid(ItemStack beside) {
        List<ItemStack> cells = new ArrayList<>(Collections.nCopies(GRID_SIDE * GRID_SIDE, ItemStack.EMPTY));
        cells.set(PAPER_CELL, new ItemStack(Items.PAPER));
        cells.set(GOO_CELL, beside);
        return cells;
    }

    /** CraftingInput.of trims empty rows and columns, so the goo's cell is found in the trimmed grid. */
    private static int gooCellOf(CraftingInput input) {
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).getItem() instanceof GooItem) {
                return slot;
            }
        }
        throw new IllegalStateException("The grid holds no goo");
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipeFor(GameTestHelper helper, CraftingInput input) {
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
    }
}
