package com.mercuriusxeno.goo.ability.nether;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the choice a slime on Gloom meets: a cell holding the turn's layers
 * turns it and keeps its layers less the turn's, and a cell holding fewer
 * leaves it a slime with its layers whole.
 * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
 */
class GloomTurnTest {

    private static final int TURN_LAYERS = 8;

    @Test
    void aCellHoldingExactlyTheTurnsLayersTurnsTheSlimeAndKeepsNone() {
        assertEquals(new GloomTurn(true, 0), GloomTurn.decide(TURN_LAYERS, TURN_LAYERS));
    }

    @Test
    void aFullCellTurnsTheSlimeAndKeepsItsLayersLessTheTurns() {
        assertEquals(new GloomTurn(true, 15 - TURN_LAYERS), GloomTurn.decide(15, TURN_LAYERS));
    }

    @Test
    void aCellOneLayerShortWaitsWithItsLayersWhole() {
        assertEquals(new GloomTurn(false, TURN_LAYERS - 1), GloomTurn.decide(TURN_LAYERS - 1, TURN_LAYERS));
    }
}
