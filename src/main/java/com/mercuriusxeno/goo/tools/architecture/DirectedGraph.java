package com.mercuriusxeno.goo.tools.architecture;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A directed graph over named nodes, each node mapped to the nodes it points
 * at. Every node is a key, so a node with no edge still stands.
 *
 * @param adjacency each node and the nodes it has an edge to, both sorted
 */
public record DirectedGraph(SortedMap<String, SortedSet<String>> adjacency) {

    /**
     * The type dependency graph: one node per scanned type, one edge per
     * reference to another type in the same list.
     *
     * @param types the scan, or a part of it
     * @return the graph keyed by qualified type name
     */
    public static DirectedGraph ofTypes(List<ScannedType> types) {
        SortedMap<String, SortedSet<String>> adjacency = new TreeMap<>();
        types.forEach(type -> adjacency.put(type.qualifiedName(), new TreeSet<>()));
        for (ScannedType type : types) {
            type.references().stream()
                    .filter(adjacency::containsKey)
                    .forEach(adjacency.get(type.qualifiedName())::add);
        }
        return new DirectedGraph(adjacency);
    }

    /**
     * The package topology graph: one node per package holding a scanned type,
     * one edge per package-to-package reference its types hold.
     *
     * @param types the scan
     * @return the graph keyed by qualified package name
     */
    public static DirectedGraph ofPackages(List<ScannedType> types) {
        Map<String, String> packageOfType = new TreeMap<>();
        types.forEach(type -> packageOfType.put(type.qualifiedName(), type.packageName()));
        SortedMap<String, SortedSet<String>> adjacency = new TreeMap<>();
        for (ScannedType type : types) {
            SortedSet<String> targets = adjacency.computeIfAbsent(type.packageName(), key -> new TreeSet<>());
            type.references().stream()
                    .filter(packageOfType::containsKey)
                    .forEach(reference -> targets.add(packageOfType.get(reference)));
            targets.remove(type.packageName());
        }
        return new DirectedGraph(adjacency);
    }

    /**
     * How many edges the graph holds.
     *
     * @return the sum of every node's out-degree
     */
    public int edgeCount() {
        return adjacency.values().stream().mapToInt(SortedSet::size).sum();
    }
}
