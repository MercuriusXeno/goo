package com.mercuriusxeno.goo.ability.xeno;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the choice a slime on Ooze meets against a turn of 8 layers: a cell
 * of 9 turns it and keeps 1, a cell of 8 turns it and keeps none, and a cell
 * of 7 leaves it a slime with its 7 whole.
 * ooze-is-xenos-world-goo-that-turns-slimes-into-random-hostiles
 */
class OozeTurnTest {

    private static final int TURN_LAYERS = 8;

    @Test
    void aCellOfNineTurnsTheSlimeAndKeepsOne() {
        assertEquals(new OozeTurn(true, 1), OozeTurn.decide(9, TURN_LAYERS));
    }

    @Test
    void aCellOfEightTurnsTheSlimeAndKeepsNone() {
        assertEquals(new OozeTurn(true, 0), OozeTurn.decide(8, TURN_LAYERS));
    }

    @Test
    void aCellOfSevenLeavesTheSlimeWithItsLayersWhole() {
        assertEquals(new OozeTurn(false, 7), OozeTurn.decide(7, TURN_LAYERS));
    }
}
