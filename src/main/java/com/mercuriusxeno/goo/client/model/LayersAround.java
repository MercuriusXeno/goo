package com.mercuriusxeno.goo.client.model;

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
