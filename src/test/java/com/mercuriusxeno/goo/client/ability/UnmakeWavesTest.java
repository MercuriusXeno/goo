package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unmake's rings travel outward from the glove, spaced evenly, each leaving
 * the glove again once it reaches the cone's end
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class UnmakeWavesTest {

    private static final double DELTA = 1e-9;

    @Test
    void ringsTravelOutward() {
        assertTrue(UnmakeWaves.ringAlong(0, 2) > UnmakeWaves.ringAlong(0, 1));
    }

    @Test
    void ringsAreSpacedEvenly() {
        assertEquals(1.0 / UnmakeWaves.RINGS, UnmakeWaves.ringAlong(1, 0), DELTA);
    }

    @Test
    void aRingReachingTheEndLeavesTheGloveAgain() {
        double lap = 1 / UnmakeWaves.RING_SPEED;
        assertEquals(UnmakeWaves.ringAlong(0, 1), UnmakeWaves.ringAlong(0, 1 + lap), 1e-6);
    }
}
