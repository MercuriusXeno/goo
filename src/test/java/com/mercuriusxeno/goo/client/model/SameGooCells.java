package com.mercuriusxeno.goo.client.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A neighbourhood of cells of one goo by offset from a cell at the origin,
 * for tests of the slime core rule; every offset left unset holds none.
 */
final class SameGooCells implements LayersAround {

    private final Map<List<Integer>, Integer> layers = new HashMap<>();

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

    @Override
    public int layersAt(int dx, int dy, int dz) {
        return layers.getOrDefault(List.of(dx, dy, dz), 0);
    }
}
