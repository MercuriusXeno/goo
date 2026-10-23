package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Each manual value the undercut litmus caught, with the chains of items its derived value came through, back to
 * the hand-keyed entries whose keyed value held. The litmus is the LCD rule's heuristic, a cheaper derived total
 * replacing the keyed one; it marks an item to judge, not a fault. This reads the registry after derivation and
 * leaves the derivation as it is.
 * decision workbench-traces-each-undercut-manual-value-to-its-roots
 */
public final class GooUndercutChains {
    /**
     * The most items one chain walks: a chain runs a few recipes, and the walk must end on a graph whose recipe
     * links reach far.
     */
    static final int MAX_CHAIN_ITEMS = 16;

    private GooUndercutChains() {
    }

    /**
     * One item on a chain.
     *
     * @param item           the item
     * @param solvedBackward true where the item's value was solved from its recipe's output rather than its inputs
     */
    public record Step(Identifier item, boolean solvedBackward) {
    }

    /**
     * One hand-keyed item a cheaper derived value replaced.
     *
     * @param item    the item
     * @param keyed   the value keyed by hand
     * @param derived the cheaper value the derivation reached
     * @param chains  each chain, entry first and this item last, sorted by its text
     */
    public record Undercut(Identifier item, GooValue keyed, GooValue derived, List<List<Step>> chains) {
    }

    /**
     * @param values a registry after derivation
     * @return each undercut manual value with its chains, in id order
     */
    public static List<Undercut> traceEachUndercut(GooValueRegistry values) {
        Map<Identifier, GooValueRegistry.ValueConflict> undercutByItem = new TreeMap<>();
        values.diagnostics().conflicts().stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper)
                .forEach(conflict -> undercutByItem.put(conflict.item(), conflict));
        Set<Identifier> entries = new HashSet<>(values.handKeyedItems());
        entries.removeAll(undercutByItem.keySet());
        ChainWalk walk = new ChainWalk(values.valueSettingRecipeByItem(), entries);
        List<Undercut> undercuts = new ArrayList<>();
        undercutByItem.forEach((item, conflict) -> undercuts.add(
                new Undercut(item, conflict.baseValue(), conflict.recipeValue(), walk.chainsTo(item))));
        return List.copyOf(undercuts);
    }

    /**
     * The walk from an undercut item back through each value-setting recipe to the entries.
     */
    private static final class ChainWalk {
        private static final Comparator<List<Step>> CHAIN_ORDER = Comparator.comparing(chain -> chain.stream()
                .map(step -> step.item().toString()).toList().toString());
        private final Map<Identifier, RecipeInput> sources;
        private final Set<Identifier> entries;

        ChainWalk(Map<Identifier, RecipeInput> sources, Set<Identifier> entries) {
            this.sources = sources;
            this.entries = entries;
        }

        List<List<Step>> chainsTo(Identifier undercut) {
            List<List<Step>> chains = new ArrayList<>();
            walk(undercut, new ArrayList<>(), new HashSet<>(), chains);
            return chains.stream().distinct().sorted(CHAIN_ORDER).toList();
        }

        /**
         * @param item    the item reached
         * @param path    the steps from the undercut item to the one before this, undercut item first
         * @param visited the items on the path, which the walk enters once
         * @param chains  where each chain reaching an entry lands, entry first
         */
        private void walk(Identifier item, List<Step> path, Set<Identifier> visited, List<List<Step>> chains) {
            if (!path.isEmpty() && entries.contains(item)) {
                chains.add(entryFirst(path, item));
                return;
            }
            RecipeInput recipe = sources.get(item);
            if (recipe == null || path.size() + 1 >= MAX_CHAIN_ITEMS) {
                return;
            }
            // decision derivation-solves-whichever-side-of-a-recipe-is-unknown
            boolean backward = !recipe.output().equals(item);
            path.add(new Step(item, backward));
            visited.add(item);
            for (Identifier next : itemsBefore(recipe, backward)) {
                walkUnvisited(next, path, visited, chains);
            }
            visited.remove(item);
            path.removeLast();
        }

        private void walkUnvisited(Identifier next, List<Step> path, Set<Identifier> visited,
                                   List<List<Step>> chains) {
            if (!visited.contains(next)) {
                walk(next, path, visited, chains);
            }
        }

        /**
         * @param recipe   the item's value-setting recipe
         * @param backward true where the recipe solved the item from its output
         * @return the items the value came from: the output for a backward solve, the slot picks otherwise
         */
        private static Set<Identifier> itemsBefore(RecipeInput recipe, boolean backward) {
            return backward ? Set.of(recipe.output()) : slotPicks(recipe);
        }

        private static List<Step> entryFirst(List<Step> path, Identifier entry) {
            List<Step> chain = new ArrayList<>(path);
            chain.add(new Step(entry, false));
            Collections.reverse(chain);
            return List.copyOf(chain);
        }

        /**
         * @param recipe a value-setting recipe, its slots narrowed to their cheapest alternatives
         * @return each slot's first cheapest alternative in id order
         */
        private static Set<Identifier> slotPicks(RecipeInput recipe) {
            Set<Identifier> picks = new TreeSet<>();
            recipe.ingredientAlternatives().forEach(slot -> slot.stream().sorted().findFirst().ifPresent(picks::add));
            return picks;
        }
    }
}
