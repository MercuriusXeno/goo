package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out each item's least value per goo type from the recipe graph itself, so every goo type
 * every item holds comes out at 1 or more. Every item starts at 1 in each type it holds; a recipe
 * making n items, with an input admitted by k of its slots, needs that input's share in each type to
 * be at least n / k times the output's least value, carried back to a fixpoint. Each bound is an exact
 * fraction with the floor it melts into and the ceiling it costs, traced to the recipe chain forcing
 * it. A hand-keyed value stops nothing: the ratios alone decide. The audit runs it, never a server load.
 * Decision audit-works-the-least-number-per-type-from-the-recipe-graph.
 */
public final class LeastNumberPerType {

    /**
     * A bound past this never settles: only a cycle demanding ever more grows it this far.
     */
    static final GooFraction DIVERGENT_BOUND = GooFraction.of(1L << 30);
    private static final GooFraction ONE = GooFraction.of(1);
    private static final String STEP_SEPARATOR = " ";
    private static final String MAKES = " -> ";
    private static final String LINE_INDENT = "  ";
    private static final String ROOT_INDENT = "    ";
    private static final String SEPARATOR = ": ";
    private static final String LIST_SEPARATOR = ", ";
    private static final String NET_OF = " net of ";
    private static final String EQUALS = " = ";
    private static final String AT = " at ";
    private static final String MELTS = " (melts ";
    private static final String COSTS = ", costs ";
    private static final String ITEM_COSTS = "; costs ";
    private static final String NO_LEAST_VALUE = "no least value";
    private static final String CYCLE = " (cycle: ";
    private static final String CYCLE_NEVER_SETTLES = " (a cycle never settles)";
    private static final String NO_ITEM = "1 (no item in the recipe graph)";
    private static final String CLOSE = ")";
    private static final String NOTHING = "";

    private LeastNumberPerType() {
    }

    private static String meltAndCost(GooFraction bound) {
        return MELTS + bound.floor() + COSTS + bound.ceiling() + CLOSE;
    }

    /**
     * One item's least value in one goo type.
     *
     * @param item       the item
     * @param bound      the least value the type must hold, 1 or more
     * @param resolvable false when a cycle's demand never settles
     * @param netOf      the container a recipe hands back for it, whose value its bound is net of, or null
     * @param steps      each recipe step forcing the bound, the item first, as "k input -> n output"
     * @param cycle      the steps forming the cycle when unresolvable, else empty
     */
    public record ItemBound(Identifier item, GooFraction bound, boolean resolvable, @Nullable Identifier netOf,
                            List<String> steps, List<String> cycle) {

        /**
         * @return the item's report line
         */
        public String line() {
            String head = ROOT_INDENT + item + SEPARATOR;
            if (!resolvable) {
                return head + NO_LEAST_VALUE + CYCLE + String.join(LIST_SEPARATOR, cycle) + CLOSE;
            }
            String net = netOf == null ? NOTHING : NET_OF + netOf;
            String chain = steps.isEmpty() ? NOTHING : EQUALS + String.join(LIST_SEPARATOR, steps);
            return head + bound + net + meltAndCost(bound) + chain;
        }
    }

    /**
     * One goo type's least values.
     *
     * @param type       the type's id
     * @param highest    the item holding the highest bound, or null when no resolvable item holds the type
     * @param resolvable false when any of its items holds no least value
     * @param items      the items in the recipe graph holding the type, by id
     */
    public record TypeLeastValue(String type, @Nullable ItemBound highest, boolean resolvable,
                                 List<ItemBound> items) {

        /**
         * @return the type's line, naming the item with the highest bound, then one line per item
         */
        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            String verdict;
            if (items.isEmpty() || highest == null) {
                verdict = NO_ITEM;
            } else if (resolvable) {
                verdict = highest.bound() + AT + highest.item() + meltAndCost(highest.bound());
            } else {
                verdict = NO_LEAST_VALUE + CYCLE_NEVER_SETTLES;
            }
            lines.add(LINE_INDENT + type + SEPARATOR + verdict);
            items.forEach(item -> lines.add(item.line()));
            return lines;
        }
    }

    /**
     * Works every type's least values.
     *
     * @param recipes         the recipes that set a value, one per output
     * @param effectiveValues every item holding a value, whose goo types each item must hold at 1 or more
     * @param deniedItems     items denied a value; a recipe making one forces nothing
     * @param types           the goo types to answer, in report order
     * @return one answer per type, listing every item of the recipe graph holding it
     */
    public static List<TypeLeastValue> work(List<RecipeInput> recipes, Map<Identifier, GooValue> effectiveValues,
                                            Set<Identifier> deniedItems, List<ResourceKey<GooTypeDefinition>> types) {
        Graph graph = new Graph(recipes, effectiveValues, deniedItems);
        graph.settle();
        List<TypeLeastValue> answers = new ArrayList<>();
        for (ResourceKey<GooTypeDefinition> type : types) {
            List<ItemBound> items = graph.items().stream().filter(item -> graph.holds(item, type)).sorted()
                    .map(item -> graph.itemBound(item, type)).toList();
            answers.add(typeLeastValue(GooTypes.id(type), items));
        }
        return answers;
    }

    private static TypeLeastValue typeLeastValue(String type, List<ItemBound> items) {
        ItemBound highest = null;
        boolean resolvable = true;
        for (ItemBound item : items) {
            if (!item.resolvable()) {
                resolvable = false;
            } else if (highest == null || item.bound().compareTo(highest.bound()) > 0) {
                highest = item;
            }
        }
        return new TypeLeastValue(type, highest, resolvable, items);
    }

    /**
     * Every item of the recipe graph once, with its least value: an item no winning recipe makes
     * starts at its bound in each goo type it holds, and every other item derives from those through
     * its winning recipe, dividing exactly.
     *
     * @param recipes         the recipes that set a value, one per output
     * @param effectiveValues every item holding a value, whose goo types a starting item holds
     * @param deniedItems     items denied a value; a recipe making one forces nothing
     * @return one line per item, by id, with the floor it melts into and the ceiling it costs
     */
    public static List<String> smallestValueLines(List<RecipeInput> recipes, Map<Identifier, GooValue> effectiveValues,
                                                  Set<Identifier> deniedItems) {
        Graph graph = new Graph(recipes, effectiveValues, deniedItems);
        graph.settle();
        Map<Identifier, GooValue> values = graph.smallestValues();
        List<String> lines = new ArrayList<>();
        values.keySet().stream().sorted().forEach(item -> lines.add(ROOT_INDENT + item + SEPARATOR
                + amountsText(values.get(item), fraction -> fraction) + MELTS
                + amountsText(values.get(item), fraction -> fraction.floor()) + ITEM_COSTS
                + amountsText(values.get(item), fraction -> fraction.ceiling()) + CLOSE));
        return lines;
    }

    private static String amountsText(GooValue value, Function<GooFraction, Object> shown) {
        return value.exactAll().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(GooTypes.ORDER))
                .map(e -> GooTypes.id(e.getKey()) + STEP_SEPARATOR + shown.apply(e.getValue()))
                .collect(Collectors.joining(LIST_SEPARATOR));
    }

    /**
     * One recipe as the graph reads it: its valued inputs, each with the count of slots admitting it.
     *
     * @param output     the item it makes
     * @param count      how many it makes, net of any copy of the output fed back in
     * @param inputs     each valued input to the number of slots admitting it
     * @param containers each input to the container the recipe hands back for it
     * @param picks      one valued alternative per slot, the lowest id, to sum a value from
     */
    private record Edge(Identifier output, int count, Map<Identifier, Integer> inputs,
                        Map<Identifier, Identifier> containers, List<Identifier> picks) {

        private String step(Identifier input) {
            return inputs.get(input) + STEP_SEPARATOR + input + MAKES + count + STEP_SEPARATOR + output;
        }
    }

    /**
     * The recipe graph and each item's bound per goo type, as it settles.
     */
    private static final class Graph {
        private final Map<Identifier, List<Edge>> edgesByOutput = new HashMap<>();
        private final List<Edge> edges = new ArrayList<>();
        private final Map<Identifier, GooValue> effectiveValues;
        private final Map<Identifier, Map<ResourceKey<GooTypeDefinition>, GooFraction>> bounds = new HashMap<>();
        private final Map<Identifier, Map<ResourceKey<GooTypeDefinition>, Edge>> forcedBy = new HashMap<>();
        private final Map<Identifier, Edge> lastRaisedBy = new HashMap<>();
        private final Set<Identifier> divergent = new HashSet<>();
        private final Map<Identifier, Identifier> netOf = new HashMap<>();
        private final Set<Identifier> seededNet = new HashSet<>();

        Graph(List<RecipeInput> recipes, Map<Identifier, GooValue> effectiveValues, Set<Identifier> denied) {
            this.effectiveValues = effectiveValues;
            for (RecipeInput recipe : recipes) {
                Edge edge = denied.contains(recipe.output()) ? null : edgeOf(recipe, effectiveValues.keySet());
                if (edge != null) {
                    edges.add(edge);
                    edgesByOutput.computeIfAbsent(edge.output(), k -> new ArrayList<>()).add(edge);
                }
            }
            for (Identifier item : items()) {
                GooValue held = effectiveValues.get(item);
                if (held != null) {
                    Map<ResourceKey<GooTypeDefinition>, GooFraction> floor = new HashMap<>();
                    held.exactAll().keySet().forEach(type -> floor.put(type, ONE));
                    bounds.put(item, floor);
                }
            }
        }

        private static @Nullable Edge edgeOf(RecipeInput recipe, Set<Identifier> valued) {
            Map<Identifier, Integer> inputs = new LinkedHashMap<>();
            List<Identifier> picks = new ArrayList<>();
            int netCount = recipe.resultCount();
            for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
                if (slot.contains(recipe.output())) {
                    // A copy of the output fed back in comes straight back out, so the recipe nets it out
                    // (operator ruling on template duplication, decision
                    // audit-works-the-least-number-per-type-from-the-recipe-graph).
                    netCount--;
                } else if (!countValuedAlternatives(slot, valued, inputs, picks)) {
                    return null;
                }
            }
            if (inputs.isEmpty() || netCount <= 0) {
                return null;
            }
            return new Edge(recipe.output(), netCount, inputs, recipe.containerItems(), picks);
        }

        /**
         * Counts each valued alternative a slot admits as one more slot admitting it.
         *
         * @param slot   the slot's alternatives
         * @param valued every item holding a value
         * @param inputs each valued input to the number of slots admitting it, added to
         * @param picks  the slot's lowest-id valued alternative, added to
         * @return false when the slot admits no valued alternative, so the recipe carries no value
         */
        private static boolean countValuedAlternatives(Set<Identifier> slot, Set<Identifier> valued,
                                                       Map<Identifier, Integer> inputs, List<Identifier> picks) {
            List<Identifier> slotValued = slot.stream().filter(valued::contains).sorted().toList();
            slotValued.forEach(alternative -> inputs.merge(alternative, 1, Integer::sum));
            if (slotValued.isEmpty()) {
                return false;
            }
            picks.add(slotValued.getFirst());
            return true;
        }

        boolean holds(Identifier item, ResourceKey<GooTypeDefinition> type) {
            return bounds.getOrDefault(item, Map.of()).containsKey(type);
        }

        void settle() {
            Deque<Edge> pending = new ArrayDeque<>(edges);
            Set<Edge> queued = new HashSet<>(edges);
            while (!pending.isEmpty()) {
                Edge edge = pending.poll();
                queued.remove(edge);
                for (Identifier raised : carryBack(edge)) {
                    for (Edge feeding : edgesByOutput.getOrDefault(raised, List.of())) {
                        if (queued.add(feeding)) {
                            pending.add(feeding);
                        }
                    }
                }
            }
        }

        private List<Identifier> carryBack(Edge edge) {
            List<Identifier> raised = new ArrayList<>();
            boolean outputDivergent = divergent.contains(edge.output());
            for (var input : edge.inputs().entrySet()) {
                Identifier item = input.getKey();
                Identifier container = edge.containers().get(item);
                if (container != null) {
                    netOf.put(item, container);
                }
                if (divergent.contains(item)) {
                    continue;
                }
                if (outputDivergent) {
                    markDivergent(item, edge, raised);
                } else {
                    raiseInput(edge, item, input.getValue(), raised);
                }
            }
            return raised;
        }

        /**
         * Raises each type of one input's bound that the edge requires higher, to n / k times the
         * output's bound in that type, recording the edge as what forced it. A type the input does not
         * hold takes no bound.
         *
         * @param edge   the recipe requiring it
         * @param item   the input
         * @param slots  the number of the recipe's slots admitting the input
         * @param raised the items raised so far, added to
         */
        private void raiseInput(Edge edge, Identifier item, int slots, List<Identifier> raised) {
            boolean changed;
            try {
                changed = raiseTypes(edge, item, slots);
            } catch (ArithmeticException overflow) {
                markDivergent(item, edge, raised);
                return;
            }
            if (!changed) {
                return;
            }
            if (bounds.get(item).values().stream().anyMatch(bound -> bound.compareTo(DIVERGENT_BOUND) > 0)) {
                markDivergent(item, edge, raised);
                return;
            }
            lastRaisedBy.put(item, edge);
            raised.add(item);
        }

        private boolean raiseTypes(Edge edge, Identifier item, int slots) {
            Map<ResourceKey<GooTypeDefinition>, GooFraction> held = bounds.get(item);
            boolean changed = false;
            for (var output : bounds.getOrDefault(edge.output(), Map.of()).entrySet()) {
                GooFraction current = held.get(output.getKey());
                GooFraction required = output.getValue().times(edge.count()).dividedBy(slots);
                if (current != null && required.compareTo(current) > 0) {
                    held.put(output.getKey(), required);
                    forcedBy.computeIfAbsent(item, k -> new HashMap<>()).put(output.getKey(), edge);
                    changed = true;
                }
            }
            return changed;
        }

        private void markDivergent(Identifier item, Edge edge, List<Identifier> raised) {
            divergent.add(item);
            lastRaisedBy.put(item, edge);
            raised.add(item);
        }

        /**
         * Seeds each item no winning recipe makes at its bound in each goo type it holds, then derives
         * every other item through its winning recipe until nothing more resolves.
         *
         * @return each item's least value
         */
        Map<Identifier, GooValue> smallestValues() {
            Map<Identifier, GooValue> values = new HashMap<>();
            Set<Identifier> starting = new HashSet<>(items());
            edges.forEach(edge -> starting.addAll(edge.containers().values()));
            starting.removeAll(edgesByOutput.keySet());
            for (Identifier item : starting) {
                if (canSeed(item, values)) {
                    seed(item, values);
                }
            }
            do {
                deriveAll(values);
            } while (seedStalledLoop(values));
            return values;
        }

        /**
         * Seeds an item at its bound per type, 1 for a type no recipe bounds. An item handed back as
         * a container is bounded net of it, so its seed is net of the container too.
         *
         * @param item   the item
         * @param values the values resolved so far, added to
         */
        private void seed(Identifier item, Map<Identifier, GooValue> values) {
            Map<ResourceKey<GooTypeDefinition>, GooFraction> amounts = new HashMap<>();
            Map<ResourceKey<GooTypeDefinition>, GooFraction> bound = bounds.getOrDefault(item, Map.of());
            effectiveValues.get(item).exactAll().keySet()
                    .forEach(type -> amounts.put(type, bound.getOrDefault(type, ONE)));
            values.put(item, GooValue.ofExact(amounts));
            if (netOf.containsKey(item)) {
                seededNet.add(item);
            }
        }

        private void deriveAll(Map<Identifier, GooValue> values) {
            boolean resolved = true;
            while (resolved) {
                resolved = false;
                for (Edge edge : edges) {
                    if (!values.containsKey(edge.output()) && deriveSmallest(edge, values)) {
                        resolved = true;
                    }
                }
            }
        }

        /**
         * A loop of winning recipes (ingot into nuggets, nuggets into ingot) holds no item to start
         * from, so the unresolved item with the highest bound starts at its bound.
         *
         * @param values the values resolved so far, added to
         * @return true when an item was seeded, so derivation runs again
         */
        private boolean seedStalledLoop(Map<Identifier, GooValue> values) {
            Identifier chosen = null;
            List<Identifier> unresolvedPicks = edges.stream().filter(edge -> !values.containsKey(edge.output()))
                    .flatMap(edge -> edge.picks().stream()).toList();
            for (Identifier item : unresolvedPicks) {
                if (canSeed(item, values) && outranks(item, chosen)) {
                    chosen = item;
                }
            }
            if (chosen == null) {
                return false;
            }
            seed(chosen, values);
            return true;
        }

        private boolean canSeed(Identifier item, Map<Identifier, GooValue> values) {
            return !values.containsKey(item) && effectiveValues.containsKey(item) && !divergent.contains(item);
        }

        private GooFraction boundTotal(Identifier item) {
            return bounds.getOrDefault(item, Map.of()).values().stream().reduce(GooFraction.ZERO, GooFraction::plus);
        }

        private boolean outranks(Identifier item, @Nullable Identifier chosen) {
            if (chosen == null) {
                return true;
            }
            int byBound = boundTotal(item).compareTo(boundTotal(chosen));
            return byBound != 0 ? byBound > 0 : item.compareTo(chosen) < 0;
        }

        private boolean deriveSmallest(Edge edge, Map<Identifier, GooValue> values) {
            GooValue total = GooValue.EMPTY;
            for (Identifier pick : edge.picks()) {
                GooValue value = values.get(pick);
                if (value == null) {
                    return false;
                }
                Identifier container = edge.containers().get(pick);
                GooValue containerValue = container == null || seededNet.contains(pick) ? null : values.get(container);
                total = total.add(containerValue == null ? value : value.subtract(containerValue), 1);
            }
            values.put(edge.output(), total.divide(edge.count()));
            return true;
        }

        Set<Identifier> items() {
            Set<Identifier> items = new HashSet<>();
            for (Edge edge : edges) {
                items.add(edge.output());
                items.addAll(edge.inputs().keySet());
            }
            return items;
        }

        ItemBound itemBound(Identifier item, ResourceKey<GooTypeDefinition> type) {
            if (divergent.contains(item)) {
                return new ItemBound(item, ONE, false, netOf.get(item), List.of(), cycleSteps(item));
            }
            return new ItemBound(item, bounds.get(item).get(type), true, netOf.get(item), boundSteps(item, type),
                    List.of());
        }

        /**
         * Follows one type's bound from an item through the recipe forcing it, on into that recipe's
         * output while the output's own bound in the type was forced by a recipe too.
         *
         * @param item the item
         * @param type the goo type
         * @return each step, item first
         */
        private List<String> boundSteps(Identifier item, ResourceKey<GooTypeDefinition> type) {
            List<String> steps = new ArrayList<>();
            Set<Identifier> seen = new HashSet<>();
            Identifier at = item;
            while (seen.add(at)) {
                Edge edge = forcedBy.getOrDefault(at, Map.of()).get(type);
                if (edge == null) {
                    break;
                }
                steps.add(edge.step(at));
                at = edge.output();
            }
            return steps;
        }

        private List<String> cycleSteps(Identifier item) {
            List<Identifier> path = new ArrayList<>();
            Identifier at = item;
            while (!path.contains(at) && lastRaisedBy.containsKey(at)) {
                path.add(at);
                at = lastRaisedBy.get(at).output();
            }
            int loopStart = path.indexOf(at);
            Set<String> steps = new LinkedHashSet<>();
            for (Identifier onLoop : path.subList(Math.max(loopStart, 0), path.size())) {
                steps.add(lastRaisedBy.get(onLoop).step(onLoop));
            }
            return new ArrayList<>(steps);
        }
    }
}
