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

    @Test
    void aRingWarpsSmoothlyAboutItsRim() {
        double step = 2 * Math.PI / 48;
        double here = UnmakeWaves.warpedRadius(1, 0.3, 0.1);
        double next = UnmakeWaves.warpedRadius(1, 0.3 + step, 0.1);
        org.junit.jupiter.api.Assertions.assertTrue(Math.abs(next - here) < UnmakeWaves.WARP_DEPTH * 0.5);
        org.junit.jupiter.api.Assertions.assertTrue(here <= 1 + UnmakeWaves.WARP_DEPTH + 1e-9);
    }

    @Test
    void eachRingWarpsOnItsOwnPhaseAndSpins() {
        org.junit.jupiter.api.Assertions.assertNotEquals(UnmakeWaves.warpPhase(0, 10), UnmakeWaves.warpPhase(1, 10));
        org.junit.jupiter.api.Assertions.assertTrue(UnmakeWaves.warpPhase(0, 11) > UnmakeWaves.warpPhase(0, 10));
    }

    @Test
    void theArcsForkFromARepeatableNoise() {
        assertEquals(UnmakeWaves.noise(7), UnmakeWaves.noise(7), 1e-12);
        org.junit.jupiter.api.Assertions.assertTrue(Math.abs(UnmakeWaves.signedNoise(7)) <= 1);
    }
}
