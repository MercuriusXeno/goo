package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A large crater's ejecta rays radiate from its rim at evenly spaced angles, each tapering out to the rays' reach:
 * a column on a ray's line reads on it, a column midway between two rays and a column past the reach read off.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
class EjectaRaysTest {

    private static final int RAYS = 7;
    private static final double PHASE = 0.4;
    /** Half a radius out past the rim, where every ray still runs wide. */
    private static final double NEAR_THE_RIM = 1.5;

    @Test
    void aColumnOnARaysLineReadsOnTheRay() {
        assertTrue(EjectaRays.isOnRay(PHASE + 2.0 * Math.PI * 3 / RAYS, NEAR_THE_RIM, RAYS, PHASE));
    }

    @Test
    void aColumnMidwayBetweenTwoRaysReadsOffEveryRay() {
        assertFalse(EjectaRays.isOnRay(PHASE + Math.PI / RAYS, NEAR_THE_RIM, RAYS, PHASE));
    }

    @Test
    void aColumnPastTheRaysReachReadsOffEveryRay() {
        assertFalse(EjectaRays.isOnRay(PHASE, EjectaRays.REACH + 0.1, RAYS, PHASE));
    }

    @Test
    void aColumnInsideTheCraterReadsOffEveryRay() {
        assertFalse(EjectaRays.isOnRay(PHASE, 0.5, RAYS, PHASE));
    }
}
