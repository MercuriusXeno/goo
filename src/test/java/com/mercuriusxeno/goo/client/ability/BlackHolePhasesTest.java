package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.PhasedState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlackHolePhases' hole body radius carries the nether marker's constant
 * pulse, and the hole draws nothing through the gather that precedes expand.
 */
class BlackHolePhasesTest {

    private static final float TOLERANCE = 1e-4f;
    /** A game time far from zero, as a live level's clock reads. */
    private static final float GAME_TIME = 48_213f;
    private static final float FULL_RADIUS = 6f;
    private static final int SAMPLES = 40;
    private static final int GATHER_TICKS = 20;

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

    @Test
    void holeDrawsNothingThroughTheGather() {
        PhasedState gather = new PhasedState();
        gather.enter("gather", GATHER_TICKS);
        for (int tick = 0; tick < GATHER_TICKS; tick++) {
            assertFalse(BlackHolePhases.holeDraws(gather), "the hole draws at gather tick " + tick);
            assertEquals(0f, BlackHolePhases.visibleScale(gather), 0f);
            assertEquals(0f, BlackHolePhases.diskExpansionScale(gather), 0f);
            gather.countTick();
        }
        PhasedState expand = new PhasedState();
        expand.enter("expand", GATHER_TICKS);
        assertTrue(BlackHolePhases.holeDraws(expand));
    }
}
