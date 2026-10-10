package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sight's eye opens near a fungus and shuts away from one through its frames
 * over the blink's length, never snapping (decision sight-lengthens-shift-and-outlines-fungus).
 */
class SightEyeIconTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void nearAFungusTheEyeOpensOverTheBlinkAndStaysOpen() {
        float half = SightEyeIcon.stepOpenness(0f, true, (long) (SightEyeIcon.BLINK_MILLIS / 2));
        assertEquals(0.5f, half, EPSILON);
        assertEquals(1f, SightEyeIcon.stepOpenness(half, true, (long) SightEyeIcon.BLINK_MILLIS), EPSILON);
    }

    @Test
    void awayFromFungusTheEyeShuts() {
        assertEquals(0f, SightEyeIcon.stepOpenness(1f, false, (long) SightEyeIcon.BLINK_MILLIS), EPSILON);
    }

    @Test
    void theLidPassesThroughEveryFrame() {
        assertTrue(SightEyeIcon.frameFor(0f).getPath().endsWith("eye_shut.png"));
        assertTrue(SightEyeIcon.frameFor(0.33f).getPath().endsWith("eye_third.png"));
        assertTrue(SightEyeIcon.frameFor(0.66f).getPath().endsWith("eye_two_thirds.png"));
        assertTrue(SightEyeIcon.frameFor(1f).getPath().endsWith("eye_open.png"));
    }
}
