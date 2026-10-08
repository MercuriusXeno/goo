package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sight's vignette rises over its first second, stands full, and falls over
 * the last three seconds of the sight (decision sight-lengthens-shift-and-outlines-fungus).
 */
class SightVignetteLayerTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void itRisesOverItsFirstSecond() {
        assertEquals(0f, SightVignetteLayer.fade(0, 1000), EPSILON);
        assertEquals(0.5f, SightVignetteLayer.fade(SightVignetteLayer.FADE_IN_TICKS / 2, 1000), EPSILON);
    }

    @Test
    void itStandsFullBetween() {
        assertEquals(1f, SightVignetteLayer.fade(500, 500), EPSILON);
    }

    @Test
    void itFallsOverItsLastThreeSeconds() {
        assertEquals(0.5f, SightVignetteLayer.fade(1000, SightVignetteLayer.FADE_OUT_TICKS / 2), EPSILON);
        assertEquals(0f, SightVignetteLayer.fade(1000, 0), EPSILON);
    }
}
