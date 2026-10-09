package com.mercuriusxeno.goo.client.ber.style;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The oculus's transformation runs once from its combo's start, and its
 * lids shut briefly once a period (decision oculus-prism-becomes-a-hovering-eye).
 */
class OculusStyleTest {

    private static final float EPSILON = 1e-4f;
    private static final long SINCE = 1000L;

    @Test
    void theTransformationRunsFromTheComboAndHoldsWhole() {
        assertEquals(0f, OculusStyle.transformationShare(SINCE, SINCE), EPSILON);
        assertEquals(0.5f, OculusStyle.transformationShare(SINCE + OculusStyle.TRANSFORM_TICKS / 2, SINCE), EPSILON);
        assertEquals(1f, OculusStyle.transformationShare(SINCE + 10 * OculusStyle.TRANSFORM_TICKS, SINCE), EPSILON);
    }

    @Test
    void theLidsShutAtTheMiddleOfABlink() {
        assertEquals(1f, OculusStyle.lidClosure(OculusStyle.BLINK_TICKS / 2f), EPSILON);
    }

    @Test
    void theLidsStandOpenBetweenBlinks() {
        assertEquals(0f, OculusStyle.lidClosure(OculusStyle.BLINK_PERIOD / 2f), EPSILON);
        assertEquals(0f, OculusStyle.lidClosure(0f), EPSILON);
    }

    @Test
    void theEyeBobsWithinItsAmplitude() {
        for (float tick = 0; tick < OculusStyle.HOVER_PERIOD; tick++) {
            assertTrue(Math.abs(OculusStyle.hover(tick)) <= OculusStyle.HOVER_AMPLITUDE + EPSILON);
        }
    }
}
