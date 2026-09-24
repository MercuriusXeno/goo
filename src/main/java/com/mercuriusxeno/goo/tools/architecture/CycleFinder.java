package com.mercuriusxeno.goo.tools.architecture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Finds the cycles of a directed graph as its strongly connected components
 * of two or more nodes (Tarjan): every node in a component reaches every
 * other, so two cycles sharing a node answer one component.
 */
public final class CycleFinder {

    private final DirectedGraph graph;
    private final Map<String, Integer> indexOf = new HashMap<>();
    private final Map<String, Integer> lowLinkOf = new HashMap<>();
    private final Deque<String> stack = new ArrayDeque<>();
    private final Set<String> onStack = new HashSet<>();
    private final List<List<String>> cycles = new ArrayList<>();

    /**
     * A finder over one graph.
     *
     * @param graph the graph to search
     */
    private CycleFinder(DirectedGraph graph) {
        this.graph = graph;
    }

    /**
     * The cycles a graph holds, each written as a walk that starts at the
     * component's first node in name order and returns to it.
     *
     * @param graph the graph to search
     * @return each cycle's walk, first node repeated at the end, ordered by first node
     */
    public static List<List<String>> find(DirectedGraph graph) {
        CycleFinder finder = new CycleFinder(graph);
        graph.adjacency().keySet().forEach(node -> {
            if (!finder.indexOf.containsKey(node)) {
                finder.connect(node);
            }
        });
        finder.cycles.sort((left, right) -> left.getFirst().compareTo(right.getFirst()));
        return List.copyOf(finder.cycles);
    }

    /**
     * Tarjan's visit of one node.
     *
     * @param node the node to visit
     */
    private void connect(String node) {
        int index = indexOf.size();
        indexOf.put(node, index);
        lowLinkOf.put(node, index);
        stack.push(node);
        onStack.add(node);
        for (String target : targetsOf(node)) {
            if (!indexOf.containsKey(target)) {
                connect(target);
                lowLinkOf.merge(node, lowLinkOf.get(target), Math::min);
            } else if (onStack.contains(target)) {
                lowLinkOf.merge(node, indexOf.get(target), Math::min);
            }
        }
        if (lowLinkOf.get(node).equals(indexOf.get(node))) {
            collectComponent(node);
        }
    }

    /**
     * Pops the component rooted at a node and keeps it when it holds a cycle.
     *
     * @param root the component's root
     */
    private void collectComponent(String root) {
        SortedSet<String> component = new TreeSet<>();
        String popped;
        do {
            popped = stack.pop();
            onStack.remove(popped);
            component.add(popped);
        } while (!popped.equals(root));
        if (component.size() > 1) {
            cycles.add(walkThrough(component));
        }
    }

    /**
     * Orders a component as a walk: from its first node, each step follows an
     * edge to the first unvisited member it reaches, else jumps to the first
     * unvisited member. A component that is one simple cycle answers that cycle.
     *
     * @param component the component's members
     * @return the walk, its first node repeated at the end
     */
    private List<String> walkThrough(SortedSet<String> component) {
        SortedSet<String> unvisited = new TreeSet<>(component);
        List<String> walk = new ArrayList<>();
        String current = unvisited.first();
        while (current != null) {
            walk.add(current);
            unvisited.remove(current);
            current = nextStep(current, unvisited);
        }
        walk.add(walk.getFirst());
        return walk;
    }

    /**
     * The next node of a walk.
     *
     * @param current   the node the walk stands on
     * @param unvisited the members the walk has yet to reach
     * @return the first unvisited target of the current node, else the first unvisited member, else null
     */
    private String nextStep(String current, SortedSet<String> unvisited) {
        return targetsOf(current).stream()
                .filter(unvisited::contains)
                .findFirst()
                .orElse(unvisited.isEmpty() ? null : unvisited.first());
    }

    /**
     * The nodes one node points at; a target the graph holds no key for has no edges.
     *
     * @param node the node
     * @return its targets in name order
     */
    private SortedSet<String> targetsOf(String node) {
        return graph.adjacency().getOrDefault(node, new TreeSet<>());
    }
}
