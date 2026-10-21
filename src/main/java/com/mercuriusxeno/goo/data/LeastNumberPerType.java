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
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Works out, per goo type, the smallest number every value-setting recipe divides evenly, from the
 * recipe graph itself. A recipe making n items, with an item admitted by k of its slots, needs that
 * item's value to be a multiple of n / gcd(k, n); an output that must be a multiple of d carries that
 * back to each input as (n * d) / gcd(k, n * d), iterated to a fixpoint. Each item's divisor is held
 * as prime powers, each with the recipe that forced it, so every factor of every number is traced to
 * the recipe chain proving it. A hand-keyed value stops nothing: the ratios alone decide, so a stick's
 * demand carries into the planks and the log. A type's number is the least common multiple of the
 * divisors of the items holding it. The audit runs it, never a server load.
 * Decision audit-works-the-least-number-per-type-from-the-recipe-graph.
 */
public final class LeastNumberPerType {

    /**
     * A divisor past this never settles: only a cycle demanding ever more grows it this far.
     */
    static final long DIVERGENT_DIVISOR = 1L << 40;
    private static final long SMALLEST_PRIME = 2;
    private static final String STEP_SEPARATOR = " ";
    private static final String MAKES = " -> ";
    private static final String LINE_INDENT = "  ";
    private static final String ROOT_INDENT = "    ";
    private static final String SEPARATOR = ": ";
    private static final String LIST_SEPARATOR = ", ";
    private static final String NET_OF = " net of ";
    private static final String EQUALS = " = ";
    private static final String TIMES = " * ";
    private static final String POWER = "^";
    private static final String OPEN = " (";
    private static final String NO_NUMBER = "resolvable by no number";
    private static final String CYCLE = " (cycle: ";
    private static final String OTHER_ITEMS = " (the other items read ";
    private static final String NO_ITEM = " (no item in the recipe graph)";
    private static final String CLOSE = ")";
    private static final String NOTHING = "";
    private static final String MULTIPLE = " (multiple ";
    private static final String REMAINDER = " LEAVES A REMAINDER";

    private LeastNumberPerType() {
    }

    /**
     * One prime power of a divisor and the recipe chain forcing it.
     *
     * @param prime    the prime
     * @param exponent its power in the divisor
     * @param steps    each recipe step forcing it, root first, as "k input -> n output"
     */
    public record FactorProof(long prime, int exponent, List<String> steps) {

        private String text() {
            return prime + POWER + exponent + OPEN + String.join(LIST_SEPARATOR, steps) + CLOSE;
        }
    }

    /**
     * One item's divisor.
     *
     * @param item       the item
     * @param divisor    the number its value must be a multiple of, 0 when resolvable by no number
     * @param resolvable false when a cycle's demand never settles
     * @param netOf      the container a recipe hands back for it, whose value its divisor is net of, or null
     * @param factors    each prime power of the divisor with the chain forcing it, smallest prime first
     * @param cycle      the steps forming the cycle when unresolvable, else empty
     */
    public record ItemDivisor(Identifier item, long divisor, boolean resolvable, @Nullable Identifier netOf,
                              List<FactorProof> factors, List<String> cycle) {

        /**
         * @return the item's report line
         */
        public String line() {
            String head = ROOT_INDENT + item + SEPARATOR;
            if (!resolvable) {
                return head + NO_NUMBER + CYCLE + String.join(LIST_SEPARATOR, cycle) + CLOSE;
            }
            String net = netOf == null ? NOTHING : NET_OF + netOf;
            String proof = factors.isEmpty() ? NOTHING
                    : EQUALS + factors.stream().map(FactorProof::text).collect(Collectors.joining(TIMES));
            return head + divisor + net + proof;
        }
    }

    /**
     * One goo type's number.
     *
     * @param type       the type's id
     * @param number     the least common multiple of its resolvable items' divisors
     * @param resolvable false when any of its items is resolvable by no number
     * @param items      the items in the recipe graph holding the type, by id
     */
    public record TypeLeastNumber(String type, long number, boolean resolvable, List<ItemDivisor> items) {

        /**
         * @return the type's line, naming the item supplying each prime power, then one line per item
         */
        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            String verdict;
            if (items.isEmpty()) {
                verdict = number + NO_ITEM;
            } else if (resolvable) {
                verdict = number + factorSources();
            } else {
                verdict = NO_NUMBER + OTHER_ITEMS + number + CLOSE;
            }
            lines.add(LINE_INDENT + type + SEPARATOR + verdict);
            items.forEach(item -> lines.add(item.line()));
            return lines;
        }

        private String factorSources() {
            Map<Long, FactorProof> highest = new TreeMap<>();
            Map<Long, Identifier> source = new HashMap<>();
            for (ItemDivisor item : items) {
                for (FactorProof factor : item.factors()) {
                    FactorProof held = highest.get(factor.prime());
                    if (held == null || factor.exponent() > held.exponent()) {
                        highest.put(factor.prime(), factor);
                        source.put(factor.prime(), item.item());
                    }
                }
            }
            if (highest.isEmpty()) {
                return NOTHING;
            }
            return EQUALS + highest.values().stream()
                    .map(f -> f.prime() + POWER + f.exponent() + OPEN + source.get(f.prime()) + CLOSE)
                    .collect(Collectors.joining(TIMES));
        }
    }

    /**
     * Works every type's number.
     *
     * @param recipes         the recipes that set a value, one per output
     * @param effectiveValues every item holding a value, whose goo types group the report
     * @param deniedItems     items denied a value; a recipe making one forces nothing
     * @param types           the goo types to answer, in report order
     * @return one answer per type, listing every item of the recipe graph holding it
     */
    public static List<TypeLeastNumber> work(List<RecipeInput> recipes, Map<Identifier, GooValue> effectiveValues,
                                             Set<Identifier> deniedItems, List<ResourceKey<GooTypeDefinition>> types) {
        Graph graph = new Graph(recipes, effectiveValues.keySet(), deniedItems);
        graph.settle();
        Map<String, List<ItemDivisor>> itemsByType = new LinkedHashMap<>();
        types.forEach(type -> itemsByType.put(GooTypes.id(type), new ArrayList<>()));
        graph.items().stream().filter(effectiveValues::containsKey).sorted().forEach(item -> {
            ItemDivisor divisor = graph.itemDivisor(item);
            effectiveValues.get(item).getAll().keySet().forEach(type -> {
                List<ItemDivisor> items = itemsByType.get(GooTypes.id(type));
                if (items != null) {
                    items.add(divisor);
                }
            });
        });
        List<TypeLeastNumber> answers = new ArrayList<>();
        itemsByType.forEach((type, items) -> answers.add(typeLeastNumber(type, items)));
        return answers;
    }

    /**
     * Every item of the recipe graph once, with its smallest value: an item no winning recipe makes
     * sits at its multiple in each goo type it holds, and every other item derives from those through
     * its winning recipe. A division that leaves a remainder is flagged, since the multiples should
     * leave none.
     *
     * @param recipes         the recipes that set a value, one per output
     * @param effectiveValues every item holding a value, whose goo types a starting item holds
     * @param deniedItems     items denied a value; a recipe making one forces nothing
     * @return one line per item, by id
     */
    public static List<String> smallestValueLines(List<RecipeInput> recipes, Map<Identifier, GooValue> effectiveValues,
                                                  Set<Identifier> deniedItems) {
        Graph graph = new Graph(recipes, effectiveValues.keySet(), deniedItems);
        graph.settle();
        Map<Identifier, GooValue> values = graph.smallestValues(effectiveValues);
        List<String> lines = new ArrayList<>();
        values.keySet().stream().sorted().forEach(item -> lines.add(ROOT_INDENT + item + SEPARATOR
                + valueText(values.get(item)) + MULTIPLE + graph.divisor(item) + CLOSE
                + (graph.leftRemainder(item) ? REMAINDER : NOTHING)));
        return lines;
    }

    private static String valueText(GooValue value) {
        return value.getAll().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(GooTypes.ORDER))
                .map(e -> GooTypes.id(e.getKey()) + STEP_SEPARATOR + e.getValue())
                .collect(Collectors.joining(LIST_SEPARATOR));
    }

    private static TypeLeastNumber typeLeastNumber(String type, List<ItemDivisor> items) {
        long number = 1;
        boolean resolvable = true;
        for (ItemDivisor item : items) {
            if (item.resolvable()) {
                number = lcm(number, item.divisor());
            } else {
                resolvable = false;
            }
        }
        return new TypeLeastNumber(type, number, resolvable, items);
    }

    private static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static long lcm(long a, long b) {
        try {
            return Math.multiplyExact(a / gcd(a, b), b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * @param value a positive number
     * @return its prime factorization, prime to exponent, smallest prime first
     */
    static Map<Long, Integer> primePowers(long value) {
        Map<Long, Integer> powers = new TreeMap<>();
        long rest = value;
        for (long prime = SMALLEST_PRIME; prime * prime <= rest; prime++) {
            while (rest % prime == 0) {
                powers.merge(prime, 1, Integer::sum);
                rest /= prime;
            }
        }
        if (rest > 1) {
            powers.merge(rest, 1, Integer::sum);
        }
        return powers;
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
     * The recipe graph and each item's divisor, as prime powers, as it settles.
     */
    private static final class Graph {
        private final Map<Identifier, List<Edge>> edgesByOutput = new HashMap<>();
        private final List<Edge> edges = new ArrayList<>();
        private final Map<Identifier, Map<Long, Integer>> exponents = new HashMap<>();
        private final Map<Identifier, Map<Long, Edge>> forcedBy = new HashMap<>();
        private final Map<Identifier, Edge> lastRaisedBy = new HashMap<>();
        private final Set<Identifier> divergent = new HashSet<>();
        private final Map<Identifier, Identifier> netOf = new HashMap<>();

        Graph(List<RecipeInput> recipes, Set<Identifier> valued, Set<Identifier> denied) {
            for (RecipeInput recipe : recipes) {
                Edge edge = denied.contains(recipe.output()) ? null : edgeOf(recipe, valued);
                if (edge != null) {
                    edges.add(edge);
                    edgesByOutput.computeIfAbsent(edge.output(), k -> new ArrayList<>()).add(edge);
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

        private int exponent(Identifier item, long prime) {
            return exponents.getOrDefault(item, Map.of()).getOrDefault(prime, 0);
        }

        long divisor(Identifier item) {
            long divisor = 1;
            try {
                for (var power : exponents.getOrDefault(item, Map.of()).entrySet()) {
                    for (int i = 0; i < power.getValue(); i++) {
                        divisor = Math.multiplyExact(divisor, power.getKey());
                    }
                }
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
            return divisor;
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
            long demand = edge.count() * divisor(edge.output());
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
                    raiseInput(edge, item, demand / gcd(input.getValue(), demand), raised);
                }
            }
            return raised;
        }

        /**
         * Raises each prime power of one input's divisor that the edge requires higher, recording the
         * edge as what forced it.
         *
         * @param edge     the recipe requiring it
         * @param item     the input
         * @param required the multiple the recipe requires of the input
         * @param raised   the items raised so far, added to
         */
        private void raiseInput(Edge edge, Identifier item, long required, List<Identifier> raised) {
            boolean changed = false;
            for (var power : primePowers(required).entrySet()) {
                if (power.getValue() > exponent(item, power.getKey())) {
                    exponents.computeIfAbsent(item, k -> new TreeMap<>()).put(power.getKey(), power.getValue());
                    forcedBy.computeIfAbsent(item, k -> new HashMap<>()).put(power.getKey(), edge);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
            if (divisor(item) > DIVERGENT_DIVISOR) {
                markDivergent(item, edge, raised);
                return;
            }
            lastRaisedBy.put(item, edge);
            raised.add(item);
        }

        private void markDivergent(Identifier item, Edge edge, List<Identifier> raised) {
            divergent.add(item);
            lastRaisedBy.put(item, edge);
            raised.add(item);
        }

        private final Set<Identifier> remainders = new HashSet<>();

        boolean leftRemainder(Identifier item) {
            return remainders.contains(item);
        }

        /**
         * Seeds each item no winning recipe makes at its multiple in each goo type it holds, then
         * derives every other item through its winning recipe until nothing more resolves.
         *
         * @param effectiveValues every item holding a value, for the goo types a starting item holds
         * @return each item's smallest value
         */
        Map<Identifier, GooValue> smallestValues(Map<Identifier, GooValue> effectiveValues) {
            Map<Identifier, GooValue> values = new HashMap<>();
            Set<Identifier> starting = new HashSet<>(items());
            edges.forEach(edge -> starting.addAll(edge.containers().values()));
            starting.removeAll(edgesByOutput.keySet());
            for (Identifier item : starting) {
                GooValue held = effectiveValues.get(item);
                if (held != null && !divergent.contains(item)) {
                    Map<ResourceKey<GooTypeDefinition>, Integer> amounts = new HashMap<>();
                    held.getAll().keySet().forEach(type -> amounts.put(type, (int) divisor(item)));
                    values.put(item, new GooValue(amounts));
                }
            }
            do {
                deriveAll(values);
            } while (seedStalledLoop(values, effectiveValues));
            return values;
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
         * from, so the unresolved item with the largest multiple starts at that multiple.
         *
         * @param values          the values resolved so far, added to
         * @param effectiveValues every item holding a value, for the goo types the seed holds
         * @return true when an item was seeded, so derivation runs again
         */
        private boolean seedStalledLoop(Map<Identifier, GooValue> values, Map<Identifier, GooValue> effectiveValues) {
            Identifier seed = null;
            for (Edge edge : edges) {
                if (values.containsKey(edge.output())) {
                    continue;
                }
                for (Identifier item : edge.picks()) {
                    if (canSeed(item, values, effectiveValues) && outranks(item, seed)) {
                        seed = item;
                    }
                }
            }
            if (seed == null) {
                return false;
            }
            Map<ResourceKey<GooTypeDefinition>, Integer> amounts = new HashMap<>();
            long multiple = divisor(seed);
            effectiveValues.get(seed).getAll().keySet().forEach(type -> amounts.put(type, (int) multiple));
            values.put(seed, new GooValue(amounts));
            return true;
        }

        private boolean canSeed(Identifier item, Map<Identifier, GooValue> values,
                                Map<Identifier, GooValue> effectiveValues) {
            return !values.containsKey(item) && effectiveValues.containsKey(item) && !divergent.contains(item);
        }

        private boolean outranks(Identifier item, @Nullable Identifier seed) {
            if (seed == null || divisor(item) != divisor(seed)) {
                return seed == null || divisor(item) > divisor(seed);
            }
            return item.compareTo(seed) < 0;
        }

        private boolean deriveSmallest(Edge edge, Map<Identifier, GooValue> values) {
            GooValue total = GooValue.EMPTY;
            for (Identifier pick : edge.picks()) {
                GooValue value = values.get(pick);
                if (value == null) {
                    return false;
                }
                Identifier container = edge.containers().get(pick);
                GooValue containerValue = container == null ? null : values.get(container);
                total = total.add(containerValue == null ? value : value.subtract(containerValue), 1);
            }
            for (int amount : total.getAll().values()) {
                if (amount % edge.count() != 0) {
                    remainders.add(edge.output());
                }
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

        ItemDivisor itemDivisor(Identifier item) {
            if (divergent.contains(item)) {
                return new ItemDivisor(item, 0, false, netOf.get(item), List.of(), cycleSteps(item));
            }
            List<FactorProof> factors = new ArrayList<>();
            exponents.getOrDefault(item, Map.of()).forEach((prime, exponent) ->
                    factors.add(new FactorProof(prime, exponent, factorSteps(item, prime))));
            return new ItemDivisor(item, divisor(item), true, netOf.get(item), factors, List.of());
        }

        /**
         * Follows one prime from an item through the recipe forcing it, on into that recipe's output while
         * the output's own divisor holds the prime too.
         *
         * @param item  the item
         * @param prime the prime
         * @return each step, item first
         */
        private List<String> factorSteps(Identifier item, long prime) {
            List<String> steps = new ArrayList<>();
            Set<Identifier> seen = new HashSet<>();
            Identifier at = item;
            while (seen.add(at)) {
                Edge edge = forcedBy.getOrDefault(at, Map.of()).get(prime);
                if (edge == null) {
                    break;
                }
                steps.add(edge.step(at));
                if (exponent(edge.output(), prime) == 0) {
                    break;
                }
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
