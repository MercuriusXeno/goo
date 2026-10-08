package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unmake's hum warbles about its low resting pitch and jitters within its
 * bound (decision unmake-waves-dissolve-by-crucible-cost).
 */
class UnmakeHumTest {

    private static final float DELTA = 1e-5f;

    @Test
    void theHumRestsLowWithNoWarbleOrJitter() {
        assertEquals(UnmakeHum.BASE_PITCH, UnmakeHum.pitchAt(0, 0.5f), DELTA);
    }

    @Test
    void theHumWarblesOverTime() {
        assertNotEquals(UnmakeHum.pitchAt(0, 0.5f), UnmakeHum.pitchAt(3, 0.5f), DELTA);
    }

    @Test
    void theHumStaysWithinItsSwing() {
        float bound = UnmakeHum.WARBLE + UnmakeHum.JITTER;
        for (int age = 0; age < 100; age++) {
            for (float jitter : new float[]{0f, 1f}) {
                float off = Math.abs(UnmakeHum.pitchAt(age, jitter) - UnmakeHum.BASE_PITCH);
                assertTrue(off <= bound + DELTA, "age " + age);
            }
        }
    }
}
