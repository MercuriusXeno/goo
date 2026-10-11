package com.mercuriusxeno.goo.ability.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that Meteo reads a cell's sky as clear only when no blocking cell
 * stands over it (decision meteo-needs-a-clear-sky).
 */
class MeteorSkyTest {

    private static final int GROUND_TOP = 64;

    /**
     * A cell resting on the column's top block, or above it, sees the sky.
     */
    @Test
    void cellOnTheTopBlockSeesTheSky() {
        assertTrue(MeteorSky.clearUnder(GROUND_TOP + 1, GROUND_TOP + 1));
        assertTrue(MeteorSky.clearUnder(GROUND_TOP + 1, GROUND_TOP + 5));
    }

    /**
     * A cell under a roof, however high, does not.
     */
    @Test
    void cellUnderARoofIsCovered() {
        assertFalse(MeteorSky.clearUnder(GROUND_TOP + 10, GROUND_TOP + 1));
        assertFalse(MeteorSky.clearUnder(GROUND_TOP + 2, GROUND_TOP + 1));
    }
}
