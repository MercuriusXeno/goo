package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.*;

/**
 * Diagnostic scanners for the goo derivation pipeline: detects value conflicts
 * between base and derived maps and recipe dependency cycles. Extracted from {@link GooValueDerivation} to keep per-class
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
        if (derived != null && derived.totalExact().compareTo(base.totalExact()) != 0) {
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

}
