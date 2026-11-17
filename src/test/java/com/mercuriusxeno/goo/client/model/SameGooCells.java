package com.mercuriusxeno.goo.client.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A neighbourhood of cells of one goo by offset from a cell at the origin,
 * for tests of the slime core rule; every offset left unset holds none.
 */
final class SameGooCells implements LayersAround {

    private final Map<List<Integer>, Integer> layers = new HashMap<>();
    private final Set<List<Integer>> solids = new HashSet<>();

    /**
     * Stands a cell of the goo at an offset.
     *
     * @param dx     east of the cell
     * @param dy     above the cell
     * @param dz     south of the cell
     * @param count  the layers it holds
     * @return this neighbourhood
     */
    SameGooCells at(int dx, int dy, int dz, int count) {
        layers.put(List.of(dx, dy, dz), count);
        return this;
    }

    /**
     * Stands a solid block at an offset.
     *
     * @param dx east of the cell
     * @param dy above the cell
     * @param dz south of the cell
     * @return this neighbourhood
     */
    SameGooCells solid(int dx, int dy, int dz) {
        solids.add(List.of(dx, dy, dz));
        return this;
    }

    @Override
    public boolean solidAt(int dx, int dy, int dz) {
        return solids.contains(List.of(dx, dy, dz));
    }

    @Override
    public LayersAround shiftedBy(int ox, int oy, int oz) {
        SameGooCells shifted = new SameGooCells();
        layers.forEach((at, count) -> shifted.at(at.get(0) - ox, at.get(1) - oy, at.get(2) - oz, count));
        solids.forEach(at -> shifted.solid(at.get(0) - ox, at.get(1) - oy, at.get(2) - oz));
        return shifted;
    }

    @Override
    public int layersAt(int dx, int dy, int dz) {
        return layers.getOrDefault(List.of(dx, dy, dz), 0);
    }
}
