package com.mercuriusxeno.goo.client.model;

import net.minecraft.core.Direction;

/**
 * The layers the cells of one goo hold around a cell, by offset from it:
 * what the slime core rule reads, so the rule stays pure of any level. A
 * cell of another goo, any other block and open air all hold none.
 * slime-cores-connect-where-only-padding-parts-them
 */
@FunctionalInterface
public interface LayersAround {

    /**
     * The layers the cell of the goo at an offset holds.
     *
     * @param dx east of the cell
     * @param dy above the cell
     * @param dz south of the cell
     * @return its layers, 0 where no cell of the goo stands
     */
    int layersAt(int dx, int dy, int dz);

    /**
     * Whether a solid block, a whole block of anything but goo, stands at an
     * offset: the goo leans against it, so its top keeps its own height there.
     * goo-block-top-slopes-to-the-average-with-the-adjoining-block
     *
     * @param dx east of the cell
     * @param dy above the cell
     * @param dz south of the cell
     * @return true where a solid block stands
     */
    default boolean solidAt(int dx, int dy, int dz) {
        return false;
    }

    /**
     * The share of the volume the cell of the goo at an offset holds on its
     * floor, its whole volume for a cell attached to no side.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param dx east of the cell
     * @param dy above the cell
     * @param dz south of the cell
     * @return its floor's layers, 0 where no cell of the goo stands
     */
    default double floorShareAt(int dx, int dy, int dz) {
        return layersAt(dx, dy, dz);
    }

    /**
     * The share of the volume the cell of the goo at an offset holds on one
     * of its surfaces: its floor, a wall or its ceiling.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param dx      east of the cell
     * @param dy      above the cell
     * @param dz      south of the cell
     * @param surface the side the goo clings to, DOWN for the floor and UP for the ceiling
     * @return its layers on that surface, 0 where no cell of the goo stands or it holds none there
     */
    default double shareOn(int dx, int dy, int dz, Direction surface) {
        return surface == Direction.DOWN ? floorShareAt(dx, dy, dz) : 0;
    }

    /**
     * The same cells read about another cell of them.
     *
     * @param ox east of this neighbourhood's cell
     * @param oy above it
     * @param oz south of it
     * @return the neighbourhood of the cell at that offset
     */
    default LayersAround shiftedBy(int ox, int oy, int oz) {
        return (dx, dy, dz) -> layersAt(dx + ox, dy + oy, dz + oz);
    }

    /**
     * A cell with no cell of its goo around it.
     *
     * @param layers the layers the cell holds
     * @return the neighbourhood
     */
    static LayersAround alone(int layers) {
        return (dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? layers : 0;
    }
}
