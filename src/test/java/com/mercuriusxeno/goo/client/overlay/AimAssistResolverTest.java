package com.mercuriusxeno.goo.client.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the aim assist locking a standing marker only for the glove's selected ability (decision diagnose-then-fix-stack-key-match). */
class AimAssistResolverTest {

    private static final String FROST_SPHERE = "goo:frost_sphere";
    private static final String FROST_TUNNEL = "goo:frost_tunnel";

    @Test
    void refusesMarkerOfAnotherAbility() {
        assertFalse(AimAssistResolver.locksMarker(FROST_SPHERE, FROST_TUNNEL));
    }

    @Test
    void locksMarkerOfTheSelectedAbility() {
        assertTrue(AimAssistResolver.locksMarker(FROST_SPHERE, FROST_SPHERE));
    }

    @Test
    void refusesEveryMarkerWithNoSelection() {
        assertFalse(AimAssistResolver.locksMarker(FROST_SPHERE, null));
    }

    @Test
    void refusesMarkerForAnEmptySelection() {
        assertFalse(AimAssistResolver.locksMarker("", ""));
    }
}
