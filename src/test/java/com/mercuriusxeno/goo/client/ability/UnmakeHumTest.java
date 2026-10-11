package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unmake's thrum sweeps steadily low to mid and back to low
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class UnmakeHumTest {

    private static final float DELTA = 1e-5f;

    @Test
    void aSweepStartsLowPeaksMidAndEndsLow() {
        assertEquals(UnmakeHum.LOW_PITCH, UnmakeHum.pitchAt(0), DELTA);
        assertEquals(UnmakeHum.MID_PITCH, UnmakeHum.pitchAt(UnmakeHum.SWEEP_TICKS / 2), DELTA);
        assertEquals(UnmakeHum.LOW_PITCH, UnmakeHum.pitchAt(UnmakeHum.SWEEP_TICKS), DELTA);
    }

    @Test
    void theSweepRisesThenFallsAtASteadyPace() {
        float quarter = UnmakeHum.pitchAt(UnmakeHum.SWEEP_TICKS / 4);
        float threeQuarters = UnmakeHum.pitchAt(UnmakeHum.SWEEP_TICKS * 3 / 4);

        assertEquals(quarter, threeQuarters, DELTA);
        assertTrue(quarter > UnmakeHum.LOW_PITCH && quarter < UnmakeHum.MID_PITCH);
    }
}
