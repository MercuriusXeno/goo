package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sight's eye draws open near a fungus and shut away from one
 * (decision sight-lengthens-shift-and-outlines-fungus).
 */
class SightEyeIconTest {

    @Test
    void theEyeOpensNearAFungusAndShutsAwayFromOne() {
        assertTrue(SightEyeIcon.eyeFor(true).getPath().endsWith("eye_open.png"));
        assertTrue(SightEyeIcon.eyeFor(false).getPath().endsWith("eye_shut.png"));
        assertNotEquals(SightEyeIcon.eyeFor(true), SightEyeIcon.eyeFor(false));
    }
}
