package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A rille cuts a steep-walled channel to its full depth along its line inside lunar's basins, and leaves the ground
 * untouched away from the line and outside the maria.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
class RilleFieldTest {

    private static final double TOLERANCE = 1e-9;
    /** A maria reading well inside a basin. */
    private static final double DEEP_IN_A_BASIN = 0.5;
    /** A maria reading on highland ground, outside every basin. */
    private static final double OUTSIDE_THE_MARIA = -0.3;

    @Test
    void onTheLineInsideABasinTheRilleCutsToItsFullDepth() {
        assertEquals(-RilleField.DEPTH, RilleField.profile(0.0, DEEP_IN_A_BASIN), TOLERANCE);
    }

    @Test
    void awayFromTheLineTheRilleCutsNothing() {
        assertEquals(0.0, RilleField.profile(RilleField.HALF_WIDTH * 2.0, DEEP_IN_A_BASIN), TOLERANCE);
    }

    @Test
    void outsideTheMariaTheRilleCutsNothing() {
        assertEquals(0.0, RilleField.profile(0.0, OUTSIDE_THE_MARIA), TOLERANCE);
    }
}
