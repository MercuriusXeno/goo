package com.mercuriusxeno.goo.ability.nether;

/**
 * What a slime on Gloom meets: a cell holding the turn's layers turns it
 * into a hostile mob and keeps its layers less the turn's; a cell holding
 * fewer leaves it a slime and keeps its layers whole.
 * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
 *
 * @param turns      true where the slime turns
 * @param layersLeft the layers the cell keeps
 */
public record GloomTurn(boolean turns, int layersLeft) {

    /**
     * Decides a slime's turn on a cell.
     *
     * @param cellLayers the cell's layers
     * @param turnLayers the layers a turn spends
     * @return the turn, or the wait, with the layers the cell keeps
     */
    public static GloomTurn decide(int cellLayers, int turnLayers) {
        return cellLayers >= turnLayers ? new GloomTurn(true, cellLayers - turnLayers)
                : new GloomTurn(false, cellLayers);
    }
}
