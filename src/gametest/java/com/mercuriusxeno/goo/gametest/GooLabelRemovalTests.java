package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GooLabelItem;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametest for taking a goo label off in the crafting grid (decision
 * label-comes-off-in-the-crafting-grid): a labeled canister or vat alone crafts its
 * label and stays in the grid with its goo and the rest of its data.
 */
@RegistersGameTests
public final class GooLabelRemovalTests {
    private static final String LABEL_COMES_OFF_IN_THE_GRID = "label_comes_off_in_the_grid";

    private static final int GRID_SIDE = 3;
    private static final int VESSEL_CELL = 4;
    private static final int VOLUME = 300;
    private static final int STACKED_VATS = 2;
    private static final String CORNER = "Corner";

    private GooLabelRemovalTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LABEL_COMES_OFF_IN_THE_GRID, GooLabelRemovalTests::comesOffInTheGrid);
    }

    /**
     * A blaze-labeled canister holding blaze, with a text label and a gasket, crafts one
     * blaze label and stays in the grid with its blaze, text label and gasket and no goo
     * label; a blaze-labeled vat does the same; an unlabeled canister matches nothing.
     *
     * @param helper the gametest helper
     */
    public static void comesOffInTheGrid(GameTestHelper helper) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(canister, GooTypes.BLAZE, VOLUME);
        UUID gasket = UUID.randomUUID();
        CanisterItem.setMetadata(canister, CanisterMetadata.EMPTY.withLabel(CORNER).withTopGasketId(gasket)
                .withGooLabel(GooTypes.BLAZE));
        ItemStack canisterLeft = craftsBlazeLabel(helper, canister);
        CanisterMetadata kept = CanisterItem.getMetadata(canisterLeft);
        helper.assertTrue(canisterLeft.is(GooItems.CANISTER.get())
                        && CanisterItem.getFluidContent(canisterLeft).equals(CanisterItem.getFluidContent(canister))
                        && CORNER.equals(kept.label()) && gasket.equals(kept.topGasketId()) && kept.gooLabel() == null,
                "The canister should stay in the grid with its goo, text label and gasket and no goo label: "
                        + canisterLeft);

        ItemStack vat = new ItemStack(GooItems.VAT.get());
        VatBlockItem.addGoo(vat, GooTypes.BLAZE, VOLUME);
        vat.set(GooDataComponents.GOO_LABEL.get(), GooTypes.BLAZE);
        ItemStack vatLeft = craftsBlazeLabel(helper, vat);
        helper.assertTrue(vatLeft.is(GooItems.VAT.get())
                        && VatBlockItem.getGooContents(vatLeft).getVolume(GooTypes.BLAZE) == VOLUME
                        && VatBlockItem.getGooLabel(vatLeft) == null,
                "The vat should stay in the grid with its goo and no goo label: " + vatLeft);

        ItemStack vatStack = vat.copyWithCount(STACKED_VATS);
        helper.assertTrue(recipeFor(helper, grid(vatStack)).isEmpty(),
                "A stack of " + STACKED_VATS + " labeled vats alone should match nothing");

        helper.assertTrue(recipeFor(helper, grid(new ItemStack(GooItems.CANISTER.get()))).isEmpty(),
                "An unlabeled canister alone should match nothing");
        helper.succeed();
    }

    /**
     * Crafts a labeled vessel alone, asserts the result is one blaze label, and answers
     * what the grid keeps where the vessel stood.
     */
    private static ItemStack craftsBlazeLabel(GameTestHelper helper, ItemStack vessel) {
        CraftingInput input = grid(vessel);
        RecipeHolder<CraftingRecipe> recipe = recipeFor(helper, input)
                .orElseThrow(() -> helper.assertionException("A labeled vessel alone should match a recipe: " + vessel));
        ItemStack label = recipe.value().assemble(input);
        helper.assertTrue(label.getItem() instanceof GooLabelItem && label.getCount() == 1
                && GooLabelItem.typeOf(label) == GooTypes.BLAZE, "The craft should yield one blaze label: " + label);
        NonNullList<ItemStack> remainders = recipe.value().getRemainingItems(input);
        return remainders.stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
    }

    private static CraftingInput grid(ItemStack vessel) {
        List<ItemStack> cells = new ArrayList<>(Collections.nCopies(GRID_SIDE * GRID_SIDE, ItemStack.EMPTY));
        cells.set(VESSEL_CELL, vessel);
        return CraftingInput.of(GRID_SIDE, GRID_SIDE, cells);
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipeFor(GameTestHelper helper, CraftingInput input) {
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
    }
}
