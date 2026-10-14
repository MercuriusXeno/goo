package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Broil's heat wave eases toward full heat while the broil runs and away
 * after, and runs only while right click holds a broil
 * (decision broil-heat-wave-ring-cooks-ahead).
 */
class HeatWaveEffectTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void aRunningBroilWarmsTheWaveByAShareEachTick() {
        assertEquals(HeatWaveEffect.EASE_PER_TICK, HeatWaveEffect.easedHeat(0f, true, 1f), EPSILON);
    }

    @Test
    void anEndedBroilCoolsTheWave() {
        assertEquals(1f - HeatWaveEffect.EASE_PER_TICK, HeatWaveEffect.easedHeat(1f, false, 1f), EPSILON);
    }

    @Test
    void aLongFrameReachesItsTargetWithoutOvershooting() {
        assertEquals(1f, HeatWaveEffect.easedHeat(0f, true, 100f), EPSILON);
    }

    @Test
    void noSelectedAbilityRunsNoWave() {
        assertFalse(HeatWaveEffect.broils(null, true));
    }
}
