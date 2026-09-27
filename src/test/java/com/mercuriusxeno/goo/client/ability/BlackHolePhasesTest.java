package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlackHolePhases' hole body radius carries the nether marker's constant pulse.
 */
class BlackHolePhasesTest {

    private static final float TOLERANCE = 1e-4f;
    /** A game time far from zero, as a live level's clock reads. */
    private static final float GAME_TIME = 48_213f;
    private static final float FULL_RADIUS = 6f;
    private static final int SAMPLES = 40;

    @Test
    void pulseReturnsToItsStartAfterOnePeriod() {
        assertEquals(BlackHolePhases.holePulse(GAME_TIME),
                BlackHolePhases.holePulse(GAME_TIME + BlackHolePhases.HOLE_PULSE_PERIOD), TOLERANCE);
    }

    @Test
    void pulseStaysWithinItsAmplitudeAndReachesIt() {
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            float pulse = BlackHolePhases.holePulse(GAME_TIME + i * BlackHolePhases.HOLE_PULSE_PERIOD / SAMPLES);
            assertTrue(Math.abs(pulse - 1f) <= BlackHolePhases.HOLE_PULSE_AMPLITUDE + TOLERANCE,
                    "pulse " + pulse + " past its amplitude");
            lowest = Math.min(lowest, pulse);
            highest = Math.max(highest, pulse);
        }
        assertEquals(2 * BlackHolePhases.HOLE_PULSE_AMPLITUDE, highest - lowest, 0.01f);
    }

    @Test
    void holdPhaseRadiusChangesAQuarterPeriodLater() {
        float now = BlackHolePhases.bodyRadius(FULL_RADIUS, 1f, GAME_TIME);
        float later = BlackHolePhases.bodyRadius(FULL_RADIUS, 1f,
                GAME_TIME + BlackHolePhases.HOLE_PULSE_PERIOD / 4);
        assertNotEquals(now, later, TOLERANCE);
    }

    @Test
    void pulseRidesThePhaseScale() {
        float scale = 0.5f;
        assertEquals(FULL_RADIUS * scale * BlackHolePhases.holePulse(GAME_TIME),
                BlackHolePhases.bodyRadius(FULL_RADIUS, scale, GAME_TIME), TOLERANCE);
    }
}
