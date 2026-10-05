package com.mercuriusxeno.goo.client.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the aim assist locking a standing marker only for the glove's selected ability (decision diagnose-then-fix-stack-key-match). */
class AimAssistResolverTest {

    private static final String CRYSTAL_CLOUD = "goo:crystal_cloud";
    private static final String METAL_SPIKES = "goo:metal_spikes";

    @Test
    void refusesMarkerOfAnotherAbility() {
        assertFalse(AimAssistResolver.locksMarker(CRYSTAL_CLOUD, METAL_SPIKES));
    }

    @Test
    void locksMarkerOfTheSelectedAbility() {
        assertTrue(AimAssistResolver.locksMarker(CRYSTAL_CLOUD, CRYSTAL_CLOUD));
    }

    @Test
    void refusesEveryMarkerWithNoSelection() {
        assertFalse(AimAssistResolver.locksMarker(CRYSTAL_CLOUD, null));
    }

    @Test
    void refusesMarkerForAnEmptySelection() {
        assertFalse(AimAssistResolver.locksMarker("", ""));
    }
}
