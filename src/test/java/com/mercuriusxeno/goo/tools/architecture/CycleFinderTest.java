package com.mercuriusxeno.goo.tools.architecture;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cycle finder over hand-built graphs: an acyclic graph answers no cycle,
 * one cycle answers its walk, two cycles sharing a node answer the one
 * component both form, and every step of every walk is an edge of the graph.
 */
class CycleFinderTest {

    static Stream<Arguments> graphsAndTheirCycles() {
        return Stream.of(
                Arguments.of("acyclic",
                        Map.of("a", List.of("b", "c"), "b", List.of("c"), "c", List.of()),
                        List.of()),
                Arguments.of("one cycle beside an acyclic tail",
                        Map.of("a", List.of("b"), "b", List.of("c"), "c", List.of("a", "d"), "d", List.of()),
                        List.of(List.of("a", "b", "c", "a"))),
                Arguments.of("two cycles sharing a node",
                        Map.of("a", List.of("b"), "b", List.of("a", "c"), "c", List.of("b")),
                        List.of(List.of("a", "b", "c", "b", "a"))),
                Arguments.of("a hub every spoke returns through",
                        Map.of("h", List.of("x", "y", "z"), "x", List.of("h"), "y", List.of("h"), "z", List.of("h")),
                        List.of(List.of("h", "x", "h", "y", "h", "z", "h"))),
                Arguments.of("two disjoint cycles",
                        Map.of("a", List.of("b"), "b", List.of("a"), "x", List.of("y"), "y", List.of("x")),
                        List.of(List.of("a", "b", "a"), List.of("x", "y", "x"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("graphsAndTheirCycles")
    void find(String shape, Map<String, List<String>> edges, List<List<String>> expected) {
        assertEquals(expected, CycleFinder.find(graphOf(edges)), shape);
    }

    static Stream<Arguments> graphsWithTangledComponents() {
        return Stream.of(
                Arguments.of("members reached only through visited ones", Map.of(
                        "a", List.of("b"), "b", List.of("c", "d"), "c", List.of("a"), "d", List.of("b"))),
                Arguments.of("a chain closed by one long edge", Map.of(
                        "a", List.of("b"), "b", List.of("c"), "c", List.of("d"), "d", List.of("e"),
                        "e", List.of("a", "c"))),
                Arguments.of("two components joined one way", Map.of(
                        "a", List.of("b"), "b", List.of("a", "x"), "x", List.of("y"), "y", List.of("z"),
                        "z", List.of("x"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource({"graphsAndTheirCycles", "graphsWithTangledComponents"})
    void findWalksOnlyAlongEdges(String shape, Map<String, List<String>> edges) {
        for (List<String> walk : CycleFinder.find(graphOf(edges))) {
            assertEquals(walk.getFirst(), walk.getLast(), shape);
            for (int step = 1; step < walk.size(); step++) {
                String from = walk.get(step - 1);
                String to = walk.get(step);
                assertTrue(edges.get(from).contains(to), shape + ": " + from + " -> " + to + " is no edge");
            }
        }
    }

    private static DirectedGraph graphOf(Map<String, List<String>> edges) {
        SortedMap<String, SortedSet<String>> adjacency = new TreeMap<>();
        edges.forEach((node, targets) -> adjacency.put(node, new TreeSet<>(targets)));
        return new DirectedGraph(adjacency);
    }
}
