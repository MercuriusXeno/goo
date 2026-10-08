package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unmake's rings start evenly spaced, travel outward each at its own speed,
 * wrap back to the glove at the cone's end, and draw their static from a
 * repeatable noise (decision unmake-waves-dissolve-by-crucible-cost).
 */
class UnmakeWavesTest {

    private static final double DELTA = 1e-9;

    @Test
    void ringsStartEvenlySpaced() {
        assertEquals(1.0 / UnmakeWaves.RINGS, UnmakeWaves.ringAlong(1, 0), DELTA);
    }

    @Test
    void ringsTravelOutward() {
        assertTrue(UnmakeWaves.ringAlong(0, 1) > UnmakeWaves.ringAlong(0, 0));
    }

    @Test
    void ringsRunAtTheirOwnSpeeds() {
        double first = UnmakeWaves.ringAlong(0, 1) - UnmakeWaves.ringAlong(0, 0);
        double second = UnmakeWaves.ringAlong(1, 1) - UnmakeWaves.ringAlong(1, 0);
        assertNotEquals(first, second, DELTA);
    }

    @Test
    void aRingStaysOnTheCone() {
        for (int tick = 0; tick < 200; tick++) {
            double along = UnmakeWaves.ringAlong(3, tick);
            assertTrue(along >= 0 && along < 1, "tick " + tick);
        }
    }

    @Test
    void theStaticIsRepeatableAndSpread() {
        assertEquals(UnmakeWaves.noise(42), UnmakeWaves.noise(42), DELTA);
        assertNotEquals(UnmakeWaves.noise(42), UnmakeWaves.noise(43), DELTA);
        for (long seed = 0; seed < 100; seed++) {
            double share = UnmakeWaves.noise(seed);
            assertTrue(share >= 0 && share <= 1, "seed " + seed);
        }
    }
}
