package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Searches, per goo type, the smallest scalar {@code base} at which no expression division and no
 * recipe division leaves a remainder: each candidate from 1 upward reparses base_values with
 * {@code base} overridden and reruns {@link GooValueDerivation#derive}. A bare number in a type's
 * amounts does not scale with the base and is named as a fixed anchor. The audit runs it, never a
 * server load, since it derives once per candidate.
 * Decision audit-searches-the-least-base-per-type.
 */
public final class LeastBaseSearch {

    /**
     * The cap is the current base times this, so the search reaches past the base it would replace.
     */
    static final int CAP_FACTOR = 2;
    private static final String CONSTANTS_KEY = "_constants";
    private static final String BASE_KEY = "base";
    private static final String INTERNAL_PREFIX = "_";
    private static final String CONSTANT_PREFIX = "$";
    private static final String UNTYPED = "untyped";
    private static final String DIVIDED_BY = " / ";
    private static final String MAKES = " -> ";
    private static final String EQUALS = " = ";
    private static final String LIST_SEPARATOR = ", ";
    private static final String LINE_INDENT = "  ";
    private static final String TYPE_SEPARATOR = ": ";
    private static final String FORCED_BY = " (forced by ";
    private static final String UNFIXABLE = "unfixable by the base (";
    private static final String FIXED_ANCHORS = "; fixed anchors: ";
    private static final String NONE_UNDER = "none under ";
    private static final String REMAINING = " (remaining: ";
    private static final String CLOSE = ")";
    private static final String ANCHOR_PREFIX = "    anchor: ";
    private static final String ANCHOR_SUFFIX = " (does not scale with the base)";
    private static final Pattern INTEGER_LITERAL = Pattern.compile("-?\\d+");
    private static final String ZERO = "0";

    private LeastBaseSearch() {
    }

    /**
     * How a type's search ended.
     */
    public enum Outcome {
        /** A candidate under the cap left no remainder in the type. */
        FOUND,
        /** A division a literal anchor feeds left a remainder at every candidate. */
        UNFIXABLE,
        /** No candidate under the cap cleared the type. */
        NONE_UNDER_CAP
    }

    /**
     * One type's answer.
     *
     * @param type      the type's id, or "untyped" for scalar-constant remainders no typed entry reads
     * @param outcome   how the search ended
     * @param leastBase the least base when found, else 0
     * @param divisions the divisions bounding it: those leaving a remainder below the least base when
     *                  found, those leaving one at every candidate when unfixable, those left at the
     *                  cap otherwise
     * @param anchors   the literal amounts in the type, each "owner = amount"
     * @param cap       the last candidate searched
     */
    public record TypeLeastBase(String type, Outcome outcome, int leastBase, SortedSet<String> divisions,
                                List<String> anchors, int cap) {

        /**
         * @return the type's report line, then one line per literal anchor
         */
        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            lines.add(LINE_INDENT + type + TYPE_SEPARATOR + verdict());
            anchors.forEach(anchor -> lines.add(ANCHOR_PREFIX + anchor + ANCHOR_SUFFIX));
            return lines;
        }

        private String verdict() {
            String divisionList = String.join(LIST_SEPARATOR, divisions);
            return switch (outcome) {
                case FOUND -> divisions.isEmpty() ? String.valueOf(leastBase)
                        : leastBase + FORCED_BY + divisionList + CLOSE;
                case UNFIXABLE -> UNFIXABLE + divisionList + FIXED_ANCHORS
                        + String.join(LIST_SEPARATOR, anchors) + CLOSE;
                case NONE_UNDER_CAP -> NONE_UNDER + cap + REMAINING + divisionList + CLOSE;
            };
        }
    }

    /**
     * The cap the audit searches to: the current base times {@link #CAP_FACTOR}.
     *
     * @param mergedBaseValues the merged base_values JSON
     * @return the last candidate base to search
     */
    public static int capFor(JsonObject mergedBaseValues) {
        JsonObject constants = mergedBaseValues.getAsJsonObject(CONSTANTS_KEY);
        if (constants == null || !constants.has(BASE_KEY)) {
            return CAP_FACTOR;
        }
        return CAP_FACTOR * Math.max(1, GooIntExpressionEvaluator.resolveValue(constants.get(BASE_KEY), Map.of()));
    }

    /**
     * Searches each type's least base from 1 to the cap; a type stops when a candidate clears it, the
     * search when every type has.
     *
     * @param mergedBaseValues the merged, tag-expanded base_values JSON
     * @param recipes          the adapted recipes the derivation reads
     * @param types            the goo types to answer, in report order
     * @param baseOverride     whether base values win over derived ones, as the server derives
     * @param cap              the last candidate base
     * @return one answer per type, then an "untyped" row when scalar-constant remainders reach no type
     */
    public static List<TypeLeastBase> search(JsonObject mergedBaseValues, List<RecipeInput> recipes,
                                             List<ResourceKey<GooTypeDefinition>> types,
                                             boolean baseOverride, int cap) {
        Map<String, TypeSearch> searches = new LinkedHashMap<>();
        types.forEach(type -> searches.put(GooTypes.id(type), new TypeSearch()));
        for (int candidate = 1; candidate <= cap && searches.values().stream().anyMatch(TypeSearch::open);
             candidate++) {
            Map<String, Set<String>> divisions = divisionsAt(mergedBaseValues, recipes, baseOverride, candidate);
            if (candidate == 1) {
                divisions.keySet().forEach(type -> searches.putIfAbsent(type, new TypeSearch()));
            }
            for (var entry : searches.entrySet()) {
                entry.getValue().observe(candidate, divisions.getOrDefault(entry.getKey(), Set.of()));
            }
        }
        Map<String, List<String>> anchors = literalAnchors(mergedBaseValues);
        List<TypeLeastBase> answers = new ArrayList<>();
        searches.forEach((type, search) -> answers.add(search.answer(type, anchors.getOrDefault(type, List.of()), cap)));
        return answers;
    }

    /**
     * Every division leaving a remainder at one candidate base, by the type it falls in.
     *
     * @param mergedBaseValues the merged base_values JSON
     * @param recipes          the adapted recipes
     * @param baseOverride     whether base values win over derived ones
     * @param candidate        the base to parse at
     * @return each type's divisions, "owner / divisor" for an expression, "output -> count" for a recipe
     */
    static Map<String, Set<String>> divisionsAt(JsonObject mergedBaseValues, List<RecipeInput> recipes,
                                                boolean baseOverride, int candidate) {
        JsonObject atCandidate = mergedBaseValues.deepCopy();
        if (!atCandidate.has(CONSTANTS_KEY)) {
            atCandidate.add(CONSTANTS_KEY, new JsonObject());
        }
        atCandidate.getAsJsonObject(CONSTANTS_KEY).addProperty(BASE_KEY, candidate);
        GooValueLoader.ParseState state = GooValueLoader.ParseState.fresh();
        Map<String, Set<String>> divisions = new TreeMap<>();
        try (DivisionRemainders.Recording recording = DivisionRemainders.record()) {
            GooValueLoader.parseMergedValues(atCandidate, state);
            recording.remainders().forEach(remainder -> divisions
                    .computeIfAbsent(typeLabel(remainder.type()), k -> new TreeSet<>())
                    .add(remainder.owner() + DIVIDED_BY + remainder.divisor()));
        }
        DerivationResult derivation = GooValueDerivation.derive(recipes, state.baseValues, state.deniedItems, baseOverride);
        for (GooValueRegistry.DivisibilityLoss loss : derivation.divisibilityLosses()) {
            addRecipeDivisions(loss, state.baseValues, derivation.derivedValues(), divisions);
        }
        return divisions;
    }

    private static void addRecipeDivisions(GooValueRegistry.DivisibilityLoss loss,
                                           Map<Identifier, GooValue> baseValues,
                                           Map<Identifier, GooValue> derivedValues,
                                           Map<String, Set<String>> divisions) {
        Optional<GooValue> input = GooRecipeEvaluator.deriveFromRecipeInput(loss.recipe(), baseValues, derivedValues);
        if (input.isEmpty()) {
            return;
        }
        String division = loss.output() + MAKES + loss.outputCount();
        GooDerivationDiagnostics.remainderByType(input.get(), loss.outputCount()).keySet().forEach(type ->
                divisions.computeIfAbsent(GooTypes.id(type), k -> new TreeSet<>()).add(division));
    }

    private static String typeLabel(@Nullable ResourceKey<GooTypeDefinition> type) {
        return type == null ? UNTYPED : GooTypes.id(type);
    }

    /**
     * The bare numbers in the constants' and items' per-type amounts, by type.
     *
     * @param mergedBaseValues the merged base_values JSON
     * @return each type's anchors, "owner = amount"
     */
    static Map<String, List<String>> literalAnchors(JsonObject mergedBaseValues) {
        Map<String, List<String>> anchors = new TreeMap<>();
        JsonObject constants = mergedBaseValues.getAsJsonObject(CONSTANTS_KEY);
        if (constants != null) {
            constants.entrySet().forEach(entry ->
                    addAnchors(CONSTANT_PREFIX + entry.getKey(), entry.getValue(), anchors));
        }
        mergedBaseValues.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith(INTERNAL_PREFIX))
                .forEach(entry -> addAnchors(entry.getKey(), entry.getValue(), anchors));
        return anchors;
    }

    private static void addAnchors(String owner, JsonElement value, Map<String, List<String>> anchors) {
        if (!value.isJsonObject()) {
            return;
        }
        for (var amount : value.getAsJsonObject().entrySet()) {
            if (isLiteral(amount.getValue())) {
                ResourceKey<GooTypeDefinition> type = GooTypes.byId(amount.getKey());
                String typeId = type == null ? amount.getKey() : GooTypes.id(type);
                anchors.computeIfAbsent(typeId, k -> new ArrayList<>())
                        .add(owner + EQUALS + amount.getValue().getAsString());
            }
        }
    }

    private static boolean isLiteral(JsonElement amount) {
        if (!amount.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = amount.getAsJsonPrimitive();
        String text = primitive.getAsString().trim();
        return (primitive.isNumber() || INTEGER_LITERAL.matcher(text).matches()) && !ZERO.equals(text);
    }

    /**
     * One type's running search across candidates.
     */
    private static final class TypeSearch {
        private int leastBase;
        private final SortedSet<String> boundingDivisions = new TreeSet<>();
        private @Nullable Set<String> everyCandidateDivisions;
        private Set<String> lastDivisions = Set.of();

        boolean open() {
            return leastBase == 0;
        }

        void observe(int candidate, Set<String> divisions) {
            if (!open()) {
                return;
            }
            if (divisions.isEmpty()) {
                leastBase = candidate;
                return;
            }
            boundingDivisions.addAll(divisions);
            if (everyCandidateDivisions == null) {
                everyCandidateDivisions = new HashSet<>(divisions);
            } else {
                everyCandidateDivisions.retainAll(divisions);
            }
            lastDivisions = divisions;
        }

        TypeLeastBase answer(String type, List<String> anchors, int cap) {
            if (!open()) {
                return new TypeLeastBase(type, Outcome.FOUND, leastBase, boundingDivisions, anchors, cap);
            }
            if (!anchors.isEmpty() && everyCandidateDivisions != null && !everyCandidateDivisions.isEmpty()) {
                return new TypeLeastBase(type, Outcome.UNFIXABLE, 0, new TreeSet<>(everyCandidateDivisions),
                        anchors, cap);
            }
            return new TypeLeastBase(type, Outcome.NONE_UNDER_CAP, 0, new TreeSet<>(lastDivisions), anchors, cap);
        }
    }
}
