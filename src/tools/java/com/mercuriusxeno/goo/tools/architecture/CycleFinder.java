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
import java.util.function.Predicate;

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
     * Orders a component as a closed walk along the graph's own edges: from
     * its first node, the walk takes the shortest path inside the component
     * to the nearest unvisited member, and once every member is reached, the
     * shortest path home. Every consecutive pair is an edge, so a member may
     * repeat; a component that is one simple cycle answers that cycle.
     *
     * @param component the component's members
     * @return the walk, its first node repeated at the end
     */
    private List<String> walkThrough(SortedSet<String> component) {
        String start = component.first();
        SortedSet<String> unvisited = new TreeSet<>(component);
        unvisited.remove(start);
        List<String> walk = new ArrayList<>(List.of(start));
        while (!unvisited.isEmpty()) {
            List<String> path = shortestPath(walk.getLast(), unvisited::contains, component);
            path.forEach(unvisited::remove);
            walk.addAll(path);
        }
        walk.addAll(shortestPath(walk.getLast(), start::equals, component));
        return walk;
    }

    /**
     * The shortest path inside a component from one node to the nearest node a
     * test accepts, breadth first with targets taken in name order. Every
     * member of a strongly connected component reaches every other, so a path exists.
     *
     * @param from      the node the path leaves
     * @param isTarget  which nodes end the path
     * @param component the members the path may pass through
     * @return the path's nodes after {@code from}, ending at the target
     */
    private List<String> shortestPath(String from, Predicate<String> isTarget, Set<String> component) {
        Map<String, String> reachedFrom = new HashMap<>(Map.of(from, from));
        Deque<String> frontier = new ArrayDeque<>(List.of(from));
        while (!frontier.isEmpty()) {
            String node = frontier.poll();
            for (String target : targetsOf(node)) {
                if (!component.contains(target) || reachedFrom.containsKey(target)) {
                    continue;
                }
                reachedFrom.put(target, node);
                if (isTarget.test(target)) {
                    return pathTo(target, reachedFrom);
                }
                frontier.add(target);
            }
        }
        throw new IllegalStateException(from);
    }

    /**
     * Reads a breadth-first search's path back from its end.
     *
     * @param end         the node the path ends at
     * @param reachedFrom each reached node mapped to the node it was reached from, the search's start to itself
     * @return the path's nodes after the start, in walking order
     */
    private static List<String> pathTo(String end, Map<String, String> reachedFrom) {
        List<String> path = new ArrayList<>();
        for (String node = end; !node.equals(reachedFrom.get(node)); node = reachedFrom.get(node)) {
            path.addFirst(node);
        }
        return path;
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
