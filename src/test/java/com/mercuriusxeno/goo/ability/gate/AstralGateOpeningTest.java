package com.mercuriusxeno.goo.ability.gate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that Astral's gate reaches the lunar dimension by the overworld's
 * night and the solar dimension by its day, on any day
 * (decision astral-visits-lunar-and-solar-dimensions).
 */
class AstralGateOpeningTest {

    private static final long MIDNIGHT = 18_000L;
    private static final long NOON = 6_000L;

    /**
     * Midnight reaches lunar, noon reaches solar.
     */
    @Test
    void nightReachesLunarAndDayReachesSolar() {
        assertEquals(AstralGateOpening.LUNAR, AstralGateOpening.destinationAt(MIDNIGHT));
        assertEquals(AstralGateOpening.SOLAR, AstralGateOpening.destinationAt(NOON));
    }

    /**
     * Night runs from its first tick up to sunrise, exclusive.
     */
    @Test
    void nightBoundsAreDuskInclusiveAndDawnExclusive() {
        assertEquals(AstralGateOpening.SOLAR, AstralGateOpening.destinationAt(AstralGateOpening.NIGHT_START - 1));
        assertEquals(AstralGateOpening.LUNAR, AstralGateOpening.destinationAt(AstralGateOpening.NIGHT_START));
        assertEquals(AstralGateOpening.LUNAR, AstralGateOpening.destinationAt(AstralGateOpening.NIGHT_END - 1));
        assertEquals(AstralGateOpening.SOLAR, AstralGateOpening.destinationAt(AstralGateOpening.NIGHT_END));
    }

    /**
     * A day time many days in reads by its time of day.
     */
    @Test
    void laterDaysReadByTimeOfDay() {
        long tenDays = 10 * AstralGateOpening.DAY_LENGTH;
        assertEquals(AstralGateOpening.LUNAR, AstralGateOpening.destinationAt(tenDays + MIDNIGHT));
        assertEquals(AstralGateOpening.SOLAR, AstralGateOpening.destinationAt(tenDays + NOON));
    }
}
