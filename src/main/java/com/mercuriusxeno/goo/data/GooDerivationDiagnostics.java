package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.*;

/**
 * Diagnostic scanners for the goo derivation pipeline: detects value conflicts
 * between base and derived maps, recipe dependency cycles, and integer division
 * remainder losses. Extracted from {@link GooValueDerivation} to keep per-class
 * method counts manageable.
 */
final class GooDerivationDiagnostics {

    private GooDerivationDiagnostics() {
    }

    /**
     * Detects items where the base value and derived value disagree on total goo.
     *
     * @param baseValues    hand-keyed base values
     * @param derivedValues recipe-derived values
     * @return list of value conflicts between base and derived
     */
    static List<GooValueRegistry.ValueConflict> detectConflicts(
            Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        List<GooValueRegistry.ValueConflict> conflicts = new ArrayList<>();
        for (var entry : baseValues.entrySet()) {
            appendConflictIfMismatched(entry.getKey(), entry.getValue(), derivedValues, conflicts);
        }
        return conflicts;
    }

    /**
     * Appends a conflict entry when derived and base total goo disagree.
     *
     * @param itemId        the item to check
     * @param base          the base goo value
     * @param derivedValues recipe-derived values for lookup
     * @param conflicts     accumulator for detected conflicts
     */
    static void appendConflictIfMismatched(Identifier itemId, GooValue base,
                                           Map<Identifier, GooValue> derivedValues,
                                           List<GooValueRegistry.ValueConflict> conflicts) {
        GooValue derived = derivedValues.get(itemId);
        if (derived != null && derived.totalGoo() != base.totalGoo()) {
            conflicts.add(new GooValueRegistry.ValueConflict(itemId, base, derived));
        }
    }

    /**
     * Finds strongly connected components in the recipe dependency graph and classifies each.
     *
     * @param byOutput   recipes grouped by output item ID
     * @param baseValues hand-keyed base values (used for anchor detection)
     * @return classified recipe cycles (anchored or dead)
     */
    static List<GooValueRegistry.RecipeCycle> detectCycles(
            Map<Identifier, List<RecipeInput>> byOutput,
            Map<Identifier, GooValue> baseValues) {
        Map<Identifier, Set<Identifier>> deps = buildDependencyGraph(byOutput);
        List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(deps);
        Set<Identifier> anchored = DirectedGraphUtils.findAnchoredNodes(deps, baseValues.keySet());
        List<GooValueRegistry.RecipeCycle> cycles = new ArrayList<>();
        for (Set<Identifier> scc : sccs) {
            cycles.add(SccClassifier.classifyScc(scc, anchored, baseValues.keySet()));
        }
        return cycles;
    }

    /**
     * Builds a forward dependency graph: output item to set of input items it depends on.
     *
     * @param byOutput recipes grouped by output item ID
     * @return dependency graph as output to input-set map
     */
    static Map<Identifier, Set<Identifier>> buildDependencyGraph(
            Map<Identifier, List<RecipeInput>> byOutput) {
        Map<Identifier, Set<Identifier>> deps = new HashMap<>();
        for (var entry : byOutput.entrySet()) {
            addNonEmptyDeps(deps, entry.getKey(), entry.getValue());
        }
        return deps;
    }

    /**
     * Adds the dependency edges for one output item if it has any inputs.
     *
     * @param deps    accumulator dependency graph
     * @param output  the output item ID
     * @param recipes recipes producing this output
     */
    static void addNonEmptyDeps(Map<Identifier, Set<Identifier>> deps,
                                Identifier output, List<RecipeInput> recipes) {
        Set<Identifier> inputs = collectAllInputIds(recipes, output);
        if (!inputs.isEmpty()) {
            deps.put(output, inputs);
        }
    }

    /**
     * Collects all input item IDs from a set of recipes, excluding the given output ID.
     *
     * @param recipes the recipes to collect inputs from
     * @param exclude item ID to exclude (typically the output item)
     * @return set of all input item IDs
     */
    static Set<Identifier> collectAllInputIds(List<RecipeInput> recipes, Identifier exclude) {
        Set<Identifier> result = new HashSet<>();
        for (RecipeInput recipe : recipes) {
            collectRecipeInputIds(recipe, exclude, result);
        }
        return result;
    }

    /**
     * Adds all input IDs from one recipe's ingredient slots, skipping the excluded ID.
     *
     * @param recipe  the recipe to collect inputs from
     * @param exclude item ID to skip (typically the output item)
     * @param dest    accumulator set for input IDs
     */
    static void collectRecipeInputIds(RecipeInput recipe, Identifier exclude, Set<Identifier> dest) {
        for (Set<Identifier> alternatives : recipe.ingredientAlternatives()) {
            for (Identifier id : alternatives) {
                if (!id.equals(exclude)) {
                    dest.add(id);
                }
            }
        }
    }

    /**
     * Scans all multi-output recipes for integer division remainder losses.
     *
     * @param byOutput      recipes grouped by output item ID
     * @param baseValues    hand-keyed base values for lookup
     * @param derivedValues recipe-derived values for lookup
     * @return list of detected divisibility losses
     */
    static List<GooValueRegistry.DivisibilityLoss> detectDivisibilityLoss(
            Map<Identifier, List<RecipeInput>> byOutput,
            Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        List<GooValueRegistry.DivisibilityLoss> losses = new ArrayList<>();
        for (var entry : byOutput.entrySet()) {
            for (RecipeInput recipe : entry.getValue()) {
                checkDivisibilityLoss(entry.getKey(), recipe, baseValues, derivedValues, losses);
            }
        }
        return losses;
    }

    /**
     * Checks a single recipe for divisibility loss and appends to the losses list if found.
     *
     * @param outputId      the output item ID
     * @param recipe        the recipe to check
     * @param baseValues    hand-keyed base values for lookup
     * @param derivedValues recipe-derived values for lookup
     * @param losses        accumulator for detected losses
     */
    static void checkDivisibilityLoss(Identifier outputId, RecipeInput recipe,
                                      Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues,
                                      List<GooValueRegistry.DivisibilityLoss> losses) {
        if (recipe.resultCount() <= 1) {
            return;
        }
        Optional<GooValue> inputValue = GooRecipeEvaluator.deriveFromRecipeInput(recipe, baseValues, derivedValues);
        if (inputValue.isEmpty()) {
            return;
        }
        buildLossIfRemainder(outputId, recipe, inputValue.get(), losses);
    }

    /**
     * Appends a divisibility loss entry when integer division leaves a remainder in any type.
     * Derivation divides each type on its own, so the loss is the sum of per-type remainders.
     *
     * @param outputId   the output item ID
     * @param recipe     the recipe being checked
     * @param inputValue total input goo before division
     * @param losses     accumulator for detected losses
     */
    static void buildLossIfRemainder(Identifier outputId, RecipeInput recipe,
                                     GooValue inputValue, List<GooValueRegistry.DivisibilityLoss> losses) {
        int count = recipe.resultCount();
        int lost = remainderByType(inputValue, count).values().stream().mapToInt(Integer::intValue).sum();
        if (lost == 0) {
            return;
        }
        losses.add(new GooValueRegistry.DivisibilityLoss(
                outputId, count, inputValue.totalGoo(), inputValue.divide(count).totalGoo(), lost, recipe));
    }

    /**
     * The remainder each goo type leaves when a recipe's input divides by its output count; the least
     * base search reads it per type (decision audit-searches-the-least-base-per-type).
     *
     * @param inputValue total input goo before division
     * @param count      the recipe's output count
     * @return each type leaving a remainder, to that remainder
     */
    static Map<ResourceKey<GooTypeDefinition>, Integer> remainderByType(GooValue inputValue, int count) {
        Map<ResourceKey<GooTypeDefinition>, Integer> remainders = new HashMap<>();
        inputValue.getAll().forEach((type, amount) -> {
            if (amount % count != 0) {
                remainders.put(type, Math.abs(amount % count));
            }
        });
        return remainders;
    }
}
