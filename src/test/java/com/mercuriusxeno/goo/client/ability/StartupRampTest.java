package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StartupRamp eases a nether hole's startup frames in: the radius and the
 * opacity they draw at.
 */
class StartupRampTest {

    private static final float TOLERANCE = 1e-5f;

    @Test
    void radiusEasesInFromNothingToItsTarget() {
        assertEquals(0f, StartupRamp.radius(0f, 2f), 0f);
        assertEquals(2f, StartupRamp.radius(1f, 2f), TOLERANCE);
        float firstHalf = StartupRamp.radius(0.5f, 2f);
        assertTrue(firstHalf < 2f - firstHalf, "the radius does not ease in");
    }

    @Test
    void alphaFadesInFromNothingToFull() {
        assertEquals(0, StartupRamp.alpha(0f));
        assertEquals(128, StartupRamp.alpha(0.5f));
        assertEquals(255, StartupRamp.alpha(1f));
    }
}
