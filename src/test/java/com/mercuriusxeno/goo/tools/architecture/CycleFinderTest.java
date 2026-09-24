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

/**
 * The cycle finder over hand-built graphs: an acyclic graph answers no cycle,
 * one cycle answers its walk, and two cycles sharing a node answer the one
 * component both form.
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
                        List.of(List.of("a", "b", "c", "a"))),
                Arguments.of("two disjoint cycles",
                        Map.of("a", List.of("b"), "b", List.of("a"), "x", List.of("y"), "y", List.of("x")),
                        List.of(List.of("a", "b", "a"), List.of("x", "y", "x"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("graphsAndTheirCycles")
    void find(String shape, Map<String, List<String>> edges, List<List<String>> expected) {
        assertEquals(expected, CycleFinder.find(graphOf(edges)), shape);
    }

    private static DirectedGraph graphOf(Map<String, List<String>> edges) {
        SortedMap<String, SortedSet<String>> adjacency = new TreeMap<>();
        edges.forEach((node, targets) -> adjacency.put(node, new TreeSet<>(targets)));
        return new DirectedGraph(adjacency);
    }
}
