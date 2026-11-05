package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A _constants block's prefabs ordered by the $refs each reads rather than by file order, so a prefab reads any
 * prefab wherever it sits, and the cycles among them, whose prefabs load no value while the rest resolve.
 * Decision prefabs-resolve-by-dependency-graph-conversions-alone-stay-ordered.
 * Decision cycled-prefabs-error-and-the-rest-load.
 *
 * @param order  every prefab's name, each after every prefab its value reads
 * @param cycles each cycle's prefab names in the block's order, the cycles ordered by their first prefab
 */
record PrefabDependencyGraph(List<String> order, List<List<String>> cycles) {
    private static final String REFERENCE_PREFIX = "$";
    private static final String NAME_SEPARATOR = ", $";
    private static final String CYCLE_ERROR = " read each other in a cycle and load no value";

    /**
     * @param constants the _constants block
     * @return the block's prefabs ordered by their reads, beside its cycles
     */
    static PrefabDependencyGraph of(JsonObject constants) {
        Map<String, Set<String>> reads = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : constants.entrySet()) {
            Set<String> named = new LinkedHashSet<>();
            collectReads(entry.getValue(), named);
            named.retainAll(constants.keySet());
            reads.put(entry.getKey(), named);
        }
        return new PrefabDependencyGraph(DirectedGraphUtils.orderDependenciesFirst(reads), cyclesOf(reads));
    }

    private static List<List<String>> cyclesOf(Map<String, Set<String>> reads) {
        List<String> blockOrder = List.copyOf(reads.keySet());
        Comparator<String> byBlockOrder = Comparator.comparingInt(blockOrder::indexOf);
        List<Set<String>> found = new ArrayList<>(DirectedGraphUtils.findStronglyConnectedComponents(reads));
        reads.forEach((name, named) -> {
            if (named.contains(name)) {
                found.add(Set.of(name));
            }
        });
        List<List<String>> cycles = new ArrayList<>();
        for (Set<String> cycle : found) {
            cycles.add(cycle.stream().sorted(byBlockOrder).toList());
        }
        cycles.sort(Comparator.comparing(List::getFirst, byBlockOrder));
        return List.copyOf(cycles);
    }

    private static void collectReads(JsonElement value, Set<String> named) {
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(field -> collectReads(field.getValue(), named));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String expr = value.getAsString();
            int i = expr.indexOf('$');
            while (i >= 0) {
                int end = GooConstantParser.scanIdentifier(expr, i + 1);
                named.add(expr.substring(i + 1, end));
                i = expr.indexOf('$', end);
            }
        }
    }

    /**
     * @return every prefab standing in a cycle
     */
    Set<String> cycled() {
        Set<String> cycled = new HashSet<>();
        cycles.forEach(cycled::addAll);
        return cycled;
    }

    /**
     * @param cycle a cycle's prefab names
     * @return the error each prefab in it reads, naming every prefab in the cycle
     */
    static String cycleError(List<String> cycle) {
        return REFERENCE_PREFIX + String.join(NAME_SEPARATOR, cycle) + CYCLE_ERROR;
    }
}
