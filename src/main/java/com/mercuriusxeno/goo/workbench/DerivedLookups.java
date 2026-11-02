package com.mercuriusxeno.goo.workbench;

import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.RecipeInput;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The lookups a derived-from or family read asks of every item, each built once per read from the model, the
 * inventory and the derivation, so the per-item loop reads a map rather than rescanning the groups or the recipes
 * (decision derived-from-gets-cheaper).
 *
 * @param making            each item's value-setting recipe, where that recipe makes the item
 * @param groupByMember     each item's valuing group: the first group holding it whose #group entry stands
 * @param chosenByOutput    the first recipe the derivation chose for each output
 * @param inventoryByOutput the inventory recipes making each output, in inventory order
 */
record DerivedLookups(Map<Identifier, RecipeInput> making, Map<String, String> groupByMember,
                      Map<Identifier, RecipeInput> chosenByOutput,
                      Map<String, List<InventoryView.Recipe>> inventoryByOutput) {
    private static final String TAG_PREFIX = "#";

    /**
     * @param model     the model
     * @param inventory the inventory
     * @param derived   the derivation over the inventory's recipes
     * @return every lookup, built once
     */
    static DerivedLookups of(BaseValuesModel model, InventoryView inventory, GooBaseValuesPreview.Derived derived) {
        return new DerivedLookups(makingRecipes(derived), groupsByMember(model),
                indexChosenByOutput(derived.recipes()), indexInventoryByOutput(inventory));
    }

    /**
     * @param inventory the inventory
     * @param derived   the derivation over the inventory's recipes
     * @return the recipe lookups alone, for a read that values no group
     */
    static DerivedLookups recipesOf(InventoryView inventory, GooBaseValuesPreview.Derived derived) {
        return new DerivedLookups(Map.of(), Map.of(), indexChosenByOutput(derived.recipes()),
                indexInventoryByOutput(inventory));
    }

    /**
     * @param id the item's namespaced id
     * @return the group valuing the item, or null where none does
     */
    @Nullable String groupValuing(String id) {
        return groupByMember.get(id);
    }

    /**
     * The inventory recipe the derivation chose for the item, else the first making it.
     *
     * @param id the item's id
     * @return the recipe, or null where none makes the item
     */
    InventoryView.@Nullable Recipe recipeFor(String id) {
        List<InventoryView.Recipe> makingIt = inventoryByOutput.getOrDefault(id, List.of());
        RecipeInput chosen = chosenByOutput.get(Identifier.parse(id));
        return makingIt.stream().filter(recipe -> chosen != null
                        && recipe.toInput().ingredientAlternatives().equals(chosen.ingredientAlternatives()))
                .findFirst().orElse(makingIt.isEmpty() ? null : makingIt.getFirst());
    }

    // Each derived item's recipe is the one the derivation recorded as setting its value, so the equivalency the
    // Items tab draws is what valued the item; a recipe that solved an input backward values that input, not its
    // output, and makes nothing here.
    private static Map<Identifier, RecipeInput> makingRecipes(GooBaseValuesPreview.Derived derived) {
        Map<Identifier, RecipeInput> making = new HashMap<>();
        derived.registry().valueSettingRecipeByItem().forEach((item, recipe) -> {
            if (recipe.output().equals(item)) {
                making.put(item, recipe);
            }
        });
        return making;
    }

    private static Map<String, String> groupsByMember(BaseValuesModel model) {
        Map<String, String> byMember = new HashMap<>();
        for (GroupConvention.Group group : GroupConvention.describe(model)) {
            if (model.member(TAG_PREFIX + group.name()) != null) {
                group.members().forEach(member -> byMember.putIfAbsent(member, group.name()));
            }
        }
        return byMember;
    }

    private static Map<Identifier, RecipeInput> indexChosenByOutput(List<RecipeInput> winning) {
        Map<Identifier, RecipeInput> chosen = new HashMap<>();
        winning.forEach(recipe -> chosen.putIfAbsent(recipe.output(), recipe));
        return chosen;
    }

    private static Map<String, List<InventoryView.Recipe>> indexInventoryByOutput(InventoryView inventory) {
        Map<String, List<InventoryView.Recipe>> byOutput = new HashMap<>();
        inventory.recipes().forEach(recipe -> byOutput.computeIfAbsent(recipe.output(), output -> new ArrayList<>())
                .add(recipe));
        return byOutput;
    }
}
