package com.mercuriusxeno.goo.ability.xeno;

/**
 * What a slime on Ooze meets: a cell holding the turn's layers turns it
 * into a hostile mob and keeps its layers less the turn's; a cell holding
 * fewer leaves it a slime and keeps its layers whole.
 * ooze-is-xenos-world-goo-that-turns-slimes-into-random-hostiles
 *
 * @param turns      true where the slime turns
 * @param layersLeft the layers the cell keeps
 */
public record OozeTurn(boolean turns, int layersLeft) {

    /**
     * Decides a slime's turn on a cell.
     *
     * @param cellLayers the cell's layers
     * @param turnLayers the layers a turn spends
     * @return the turn, or the wait, with the layers the cell keeps
     */
    public static OozeTurn decide(int cellLayers, int turnLayers) {
        return cellLayers >= turnLayers ? new OozeTurn(true, cellLayers - turnLayers)
                : new OozeTurn(false, cellLayers);
    }
}
