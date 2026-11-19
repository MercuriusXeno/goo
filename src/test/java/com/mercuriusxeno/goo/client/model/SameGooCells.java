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
     * A pillar of 15, 15 and 6 layers standing up from the origin on a solid
     * floor, alone or in a film of 1 layer filling the 3 by 3 blocks round its foot.
     *
     * @param inAFilm whether the film stands round its foot
     * @return the pillar's neighbourhood, the origin its foot
     */
    static SameGooCells pillar(boolean inAFilm) {
        SameGooCells cells = new SameGooCells();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                cells.solid(x, -1, z);
                if (inAFilm && Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    cells.at(x, 0, z, 1);
                }
            }
        }
        return cells.at(0, 0, 0, 15).at(0, 1, 0, 15).at(0, 2, 0, 6);
    }

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
