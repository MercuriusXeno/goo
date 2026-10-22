package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Solves a recipe's one unknown ingredient from its valued output: the output's value times the
 * result count, less every valued slot, shared over the slots the unknown ingredient fills.
 * decision derivation-solves-whichever-side-of-a-recipe-is-unknown
 */
final class GooInputSolver {

    private final Map<Identifier, GooValue> baseValues;
    private final Map<Identifier, GooValue> derivedValues;
    private final Set<Identifier> deniedItems;

    private GooInputSolver(Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues,
                           Set<Identifier> deniedItems) {
        this.baseValues = baseValues;
        this.derivedValues = derivedValues;
        this.deniedItems = deniedItems;
    }

    /**
     * Solves every recipe holding exactly one unknown ingredient against one reading of the values, so
     * the order the recipes arrive in changes nothing; where two recipes solve one item the smaller value
     * wins and the simpler breaks a tie.
     *
     * @param recipes       every recipe to solve
     * @param baseValues    hand-keyed base values
     * @param derivedValues the values derived so far (read, never written)
     * @param deniedItems   items no recipe values
     * @return each solved item's value and the recipe solving it
     */
    static Map<Identifier, GooRecipeEvaluator.RecipeResult> solveUnknownInputs(Collection<RecipeInput> recipes,
            Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues,
            Set<Identifier> deniedItems) {
        GooInputSolver solver = new GooInputSolver(baseValues, derivedValues, deniedItems);
        Map<Identifier, GooRecipeEvaluator.RecipeResult> solved = new HashMap<>();
        for (RecipeInput recipe : recipes) {
            solver.solveRecipe(recipe, solved);
        }
        return solved;
    }

    private @Nullable GooValue valueOf(Identifier item) {
        return GooValueDerivation.lookupForDerivation(item, baseValues, derivedValues);
    }

    /**
     * Solves each unvalued alternative of the recipe's one unknown ingredient, keeping the better of
     * each item's solutions.
     *
     * @param recipe the recipe to solve
     * @param solved each item's best solution so far, added to
     */
    private void solveRecipe(RecipeInput recipe, Map<Identifier, GooRecipeEvaluator.RecipeResult> solved) {
        if (deniedItems.contains(recipe.output()) || valueOf(recipe.output()) == null) {
            return;
        }
        Unknown unknown = findTheOneUnknown(recipe);
        GooValue perSlot = unknown == null ? null : valuePerUnknownSlot(recipe, unknown);
        if (perSlot == null) {
            return;
        }
        for (Identifier item : unknown.unvalued()) {
            GooValue value = withContainerReturned(perSlot, recipe.containerItems().get(item));
            solved.merge(item, new GooRecipeEvaluator.RecipeResult(value, recipe), GooRecipeEvaluator::betterOfTwo);
        }
    }

    /**
     * @param recipe  the recipe to solve
     * @param unknown its one unknown ingredient
     * @return the share each unknown slot carries, or null where a valued slot reads no value or the
     * share runs empty or negative
     */
    private @Nullable GooValue valuePerUnknownSlot(RecipeInput recipe, Unknown unknown) {
        GooValue remaining = valueOf(recipe.output()).multiply(recipe.resultCount());
        for (var group : slotsByIngredient(recipe).entrySet()) {
            if (group.getKey().equals(unknown.alternatives())) {
                continue;
            }
            GooValue slotCost = GooRecipeEvaluator.computeSlotCost(group.getKey(), recipe.containerItems(),
                    baseValues, derivedValues);
            if (slotCost == null) {
                return null;
            }
            remaining = remaining.add(slotCost, -group.getValue());
        }
        GooValue perSlot = remaining.divide(unknown.slots());
        return perSlot.isEmpty() || perSlot.hasNegative() ? null : perSlot;
    }

    /**
     * @param recipe a recipe whose output holds a value
     * @return the recipe's one ingredient holding an unvalued alternative, or null where none or several
     * ingredients hold one, or the output feeds itself
     */
    private @Nullable Unknown findTheOneUnknown(RecipeInput recipe) {
        Unknown found = null;
        for (var group : slotsByIngredient(recipe).entrySet()) {
            Set<Identifier> unvalued = unvaluedAlternatives(group.getKey());
            if (unvalued.isEmpty()) {
                continue;
            }
            if (found != null || group.getKey().contains(recipe.output())) {
                return null;
            }
            found = new Unknown(group.getKey(), group.getValue(), unvalued);
        }
        return found;
    }

    private Set<Identifier> unvaluedAlternatives(Set<Identifier> alternatives) {
        Set<Identifier> unvalued = new HashSet<>();
        for (Identifier alternative : alternatives) {
            if (!deniedItems.contains(alternative) && valueOf(alternative) == null) {
                unvalued.add(alternative);
            }
        }
        return unvalued;
    }

    private static Map<Set<Identifier>, Integer> slotsByIngredient(RecipeInput recipe) {
        Map<Set<Identifier>, Integer> groups = new LinkedHashMap<>();
        for (Set<Identifier> alternatives : recipe.ingredientAlternatives()) {
            groups.merge(alternatives, 1, Integer::sum);
        }
        return groups;
    }

    /**
     * The forward evaluation subtracts a returned container from its ingredient, so the solved
     * ingredient carries the container back.
     *
     * @param perSlot     the share one slot carries
     * @param containerId the container the ingredient returns, or null
     * @return the ingredient's value
     */
    private GooValue withContainerReturned(GooValue perSlot, @Nullable Identifier containerId) {
        GooValue container = containerId == null ? null : valueOf(containerId);
        return container == null ? perSlot : perSlot.add(container, 1);
    }

    /**
     * The one ingredient a recipe does not value: its alternatives, the slots it fills and its
     * alternatives holding no value.
     */
    private record Unknown(Set<Identifier> alternatives, int slots, Set<Identifier> unvalued) {
    }
}
