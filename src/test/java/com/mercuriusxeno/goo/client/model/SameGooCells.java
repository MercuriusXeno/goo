package com.mercuriusxeno.goo.client.model;

import net.minecraft.core.Direction;
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
    private final Map<List<Object>, Double> shares = new HashMap<>();

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

    /**
     * Gives the cell at an offset a share of its volume on one surface.
     *
     * @param dx      east of the cell
     * @param dy      above the cell
     * @param dz      south of the cell
     * @param surface the surface, DOWN for the floor
     * @param share   the layers it holds there
     * @return this neighbourhood
     */
    SameGooCells share(int dx, int dy, int dz, Direction surface, double share) {
        shares.put(List.of(dx, dy, dz, surface), share);
        return this;
    }

    @Override
    public double shareOn(int dx, int dy, int dz, Direction surface) {
        Double share = shares.get(List.of(dx, dy, dz, surface));
        if (share != null) {
            return share;
        }
        return surface == Direction.DOWN ? layersAt(dx, dy, dz) : 0;
    }

    @Override
    public double floorShareAt(int dx, int dy, int dz) {
        return shareOn(dx, dy, dz, Direction.DOWN);
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
        shares.forEach((at, share) -> shifted.share((Integer) at.get(0) - ox, (Integer) at.get(1) - oy,
                (Integer) at.get(2) - oz, (Direction) at.get(3), share));
        return shifted;
    }

    @Override
    public int layersAt(int dx, int dy, int dz) {
        return layers.getOrDefault(List.of(dx, dy, dz), 0);
    }
}
