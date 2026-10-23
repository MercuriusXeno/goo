package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each crafting path whose crafts lose goo of one type and gain goo of another, read at the values the derivation
 * settled: a loophole that trivializes collecting a type is a path that turns a cheap type into it, so the path
 * gaining the most per craft reads first. A path is a run of recipes, each one's output an ingredient of the
 * next; a single recipe is a path of one. The audit section reads this one computation.
 * decision workbench-lists-type-to-type-paths-worst-first
 */
public final class GooConversionFlows {
    /**
     * The most recipes one path chains: a loophole runs a few crafts, and the walk must end on a recipe graph
     * where every common item feeds hundreds of recipes.
     */
    static final int MAX_PATH_RECIPES = 3;

    /**
     * The most gained per craft first, then the least lost for it; a tie falls to the last output's id, the
     * shorter path and the path's outputs, then the types, so the order reads the same every run.
     */
    private static final Comparator<Flow> FLOW_ORDER = Comparator
            .comparing(Flow::targetGained, Comparator.reverseOrder())
            .thenComparing(Flow::sourceLost)
            .thenComparing(flow -> flow.recipes().getLast().output())
            .thenComparing(flow -> flow.recipes().size())
            .thenComparing(flow -> flow.recipes().stream().map(recipe -> recipe.output().toString()).toList()
                    .toString())
            .thenComparing(flow -> GooTypes.id(flow.source()))
            .thenComparing(flow -> GooTypes.id(flow.target()));

    private GooConversionFlows() {
    }

    /**
     * One path moving goo from one type into another.
     *
     * @param recipes      the path's recipes in craft order, as the registry adapted them
     * @param source       the type the path loses
     * @param target       the type the path gains
     * @param sourceLost   the source goo the path's inputs carry beyond its outputs, per craft of the last recipe
     * @param targetGained the target goo the path's outputs carry beyond its inputs, per craft of the last recipe
     */
    public record Flow(List<RecipeInput> recipes, ResourceKey<GooTypeDefinition> source,
                       ResourceKey<GooTypeDefinition> target, GooFraction sourceLost, GooFraction targetGained) {
    }

    /**
     * @param values a registry after derivation
     * @return one flow per path and per lost-and-gained type pair, the most gained per craft first
     */
    public static List<Flow> flowsOf(GooValueRegistry values) {
        PathWalk walk = new PathWalk(new LinkedHashSet<>(values.lastRecipes()), values.table().getEffectiveValues());
        List<Flow> flows = walk.flows();
        flows.sort(FLOW_ORDER);
        return List.copyOf(flows);
    }

    /**
     * The walk from each recipe into every recipe reading its output, summing each path's goo change per craft of
     * its last recipe.
     */
    private static final class PathWalk {
        private final Map<Identifier, GooValue> effective;
        private final Set<RecipeInput> recipes;
        private final Map<Identifier, List<RecipeInput>> readersByItem = new HashMap<>();
        private final List<Flow> flows = new ArrayList<>();

        PathWalk(Set<RecipeInput> recipes, Map<Identifier, GooValue> effective) {
            this.recipes = recipes;
            this.effective = effective;
            for (RecipeInput recipe : recipes) {
                Set<Identifier> read = new HashSet<>();
                recipe.ingredientAlternatives().forEach(read::addAll);
                read.forEach(item -> readersByItem.computeIfAbsent(item, key -> new ArrayList<>()).add(recipe));
            }
        }

        List<Flow> flows() {
            for (RecipeInput recipe : recipes) {
                Map<ResourceKey<GooTypeDefinition>, GooFraction> change = changePerCraft(recipe, null);
                if (change.isEmpty()) {
                    continue;
                }
                Set<Identifier> visited = new HashSet<>(slotPicks(recipe));
                visited.add(recipe.output());
                extend(new ArrayList<>(List.of(recipe)), change, visited);
            }
            return flows;
        }

        /**
         * @param path    the path so far
         * @param change  the path's goo change per craft of its last recipe
         * @param visited the items the path holds, which no step enters twice
         */
        private void extend(List<RecipeInput> path, Map<ResourceKey<GooTypeDefinition>, GooFraction> change,
                            Set<Identifier> visited) {
            addFlows(List.copyOf(path), change);
            if (path.size() >= MAX_PATH_RECIPES) {
                return;
            }
            RecipeInput last = path.getLast();
            for (RecipeInput next : readersByItem.getOrDefault(last.output(), List.of())) {
                if (visited.contains(next.output())) {
                    continue;
                }
                Map<ResourceKey<GooTypeDefinition>, GooFraction> step = changePerCraft(next, last.output());
                if (step.isEmpty()) {
                    continue;
                }
                path.add(next);
                visited.add(next.output());
                extend(path, chained(change, last, next, step), visited);
                visited.remove(next.output());
                path.removeLast();
            }
        }

        /**
         * @param change the path's change per craft of last
         * @param last   the path's last recipe
         * @param next   the recipe reading last's output
         * @param step   next's own change per craft
         * @return the path's change per craft of next: the crafts of the path so far that one craft of next
         *         consumes, which is next's slots reading last's output over the items one craft of last makes
         */
        private static Map<ResourceKey<GooTypeDefinition>, GooFraction> chained(
                Map<ResourceKey<GooTypeDefinition>, GooFraction> change, RecipeInput last, RecipeInput next,
                Map<ResourceKey<GooTypeDefinition>, GooFraction> step) {
            long consumed = next.ingredientAlternatives().stream().filter(slot -> slot.contains(last.output()))
                    .count();
            Map<ResourceKey<GooTypeDefinition>, GooFraction> total = new HashMap<>(step);
            change.forEach((type, amount) -> total.merge(type,
                    amount.times(consumed).dividedBy(last.resultCount()), GooFraction::plus));
            return total;
        }

        /**
         * @param recipe the recipe
         * @param fed    the item the path feeds into the recipe, read in every slot holding it; null for a path's
         *               first recipe, whose slots read their cheapest alternative
         * @return per type, the output's goo less the inputs' goo for one craft; empty where an input or the
         *         output holds no value, since such a craft moves goo nobody can read
         */
        private Map<ResourceKey<GooTypeDefinition>, GooFraction> changePerCraft(RecipeInput recipe,
                                                                              @Nullable Identifier fed) {
            GooValue output = effective.get(recipe.output());
            if (output == null || output.isEmpty() || recipe.hasNoIngredients()) {
                return Map.of();
            }
            Map<ResourceKey<GooTypeDefinition>, GooFraction> change = new HashMap<>();
            output.exactAll().forEach((type, amount) -> change.merge(type, amount.times(recipe.resultCount()),
                    GooFraction::plus));
            for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
                GooValue cost = GooRecipeEvaluator.computeSlotCost(slotRead(slot, fed), recipe.containerItems(),
                        effective, Map.of());
                if (cost == null) {
                    return Map.of();
                }
                cost.exactAll().forEach((type, amount) -> change.merge(type, amount.negated(), GooFraction::plus));
            }
            return change;
        }

        /**
         * @param slot a slot's alternatives
         * @param fed  the item the path feeds in, or null
         * @return the fed item alone where the slot holds it, the whole slot otherwise
         */
        private static Set<Identifier> slotRead(Set<Identifier> slot, @Nullable Identifier fed) {
            return fed != null && slot.contains(fed) ? Set.of(fed) : slot;
        }

        private Set<Identifier> slotPicks(RecipeInput recipe) {
            Set<Identifier> picks = new HashSet<>();
            for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
                Identifier pick = IGooValueLookup.findCheapestAmong(slot, effective::get);
                if (pick != null) {
                    picks.add(pick);
                }
            }
            return picks;
        }

        private void addFlows(List<RecipeInput> path, Map<ResourceKey<GooTypeDefinition>, GooFraction> change) {
            change.forEach((source, lost) -> {
                if (!lost.isNegative()) {
                    return;
                }
                change.forEach((target, gained) -> {
                    if (gained.isPositive()) {
                        flows.add(new Flow(path, source, target, lost.negated(), gained));
                    }
                });
            });
        }
    }
}
