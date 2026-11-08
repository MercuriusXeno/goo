package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Flames' heat wave eases toward full heat while Flames runs and away
 * after, and runs only while right click holds Flames
 * (decisions broil-heat-wave-ring-cooks-ahead, flames-absorbs-broil-and-cooks-blocks).
 */
class HeatWaveEffectTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void aRunningFlameWarmsTheWaveByAShareEachTick() {
        assertEquals(HeatWaveEffect.EASE_PER_TICK, HeatWaveEffect.easedHeat(0f, true, 1f), EPSILON);
    }

    @Test
    void anEndedFlameCoolsTheWave() {
        assertEquals(1f - HeatWaveEffect.EASE_PER_TICK, HeatWaveEffect.easedHeat(1f, false, 1f), EPSILON);
    }

    @Test
    void aLongFrameReachesItsTargetWithoutOvershooting() {
        assertEquals(1f, HeatWaveEffect.easedHeat(0f, true, 100f), EPSILON);
    }

    @Test
    void noSelectedAbilityRunsNoWave() {
        assertFalse(HeatWaveEffect.holdsFlames(null, true));
    }
}
