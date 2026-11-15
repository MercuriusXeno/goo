package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cast sized by its charge costs by the volume of its radius against the
 * reference radius its JSON cost buys, and is cut back to the whole radius
 * the holdings pay for (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
 */
class ChargeSizeTest {

    private static final double DELTA = 1e-9;
    private static final int REFERENCE_COST = 1000;

    @Test
    void theCostGrowsWithTheVolume() {
        assertEquals(REFERENCE_COST, ChargeSize.costAt(REFERENCE_COST, ChargeSize.REFERENCE_RADIUS));
        assertEquals(8 * REFERENCE_COST, ChargeSize.costAt(REFERENCE_COST, 2 * ChargeSize.REFERENCE_RADIUS));
        assertEquals(REFERENCE_COST / 8, ChargeSize.costAt(REFERENCE_COST, ChargeSize.REFERENCE_RADIUS / 2));
    }

    @Test
    void theRadiusIsCutBackToWhatTheHoldingsPayFor() {
        assertEquals(ChargeSize.REFERENCE_RADIUS, ChargeSize.affordable(10, REFERENCE_COST, REFERENCE_COST), DELTA);
        assertEquals(4, ChargeSize.affordable(4, REFERENCE_COST, 100 * REFERENCE_COST), DELTA);
        assertEquals(ChargeSize.MIN_RADIUS, ChargeSize.affordable(10, REFERENCE_COST, 0), DELTA);
    }

    @Test
    void anAffordableRadiusIsTheLargestWholeOneTheHoldingsPayFor() {
        int holdings = 2_345;
        double radius = ChargeSize.affordable(50, REFERENCE_COST, holdings);
        assertEquals(Math.floor(radius), radius, DELTA);
        assertTrue(ChargeSize.costAt(REFERENCE_COST, radius) <= holdings);
        assertTrue(ChargeSize.costAt(REFERENCE_COST, radius + 1) > holdings);
    }

    @Test
    void aChargeBetweenWholeRadiiCastsAtTheWholeRadiusUnderIt() {
        assertEquals(3, ChargeSize.affordable(3.9, REFERENCE_COST, 100 * REFERENCE_COST), DELTA);
    }
}
