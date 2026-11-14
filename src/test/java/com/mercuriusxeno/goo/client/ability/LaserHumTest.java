package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LaserHum's pitch: it starts pitched up as the hold begins, glides down
 * fast then slow, and settles at the low thrum it holds after.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
class LaserHumTest {

    private static final float TOLERANCE = 1e-6f;

    @Test
    void startsPitchedUp() {
        assertEquals(LaserHum.START_PITCH, LaserHum.pitchAt(0), TOLERANCE);
    }

    @Test
    void settlesAtTheThrumAndHolds() {
        assertEquals(LaserHum.THRUM_PITCH, LaserHum.pitchAt(LaserHum.GLIDE_TICKS), TOLERANCE);
        assertEquals(LaserHum.THRUM_PITCH, LaserHum.pitchAt(LaserHum.GLIDE_TICKS * 10), TOLERANCE);
    }

    @Test
    void glidesDownFastThenSlow() {
        float quarter = LaserHum.pitchAt(LaserHum.GLIDE_TICKS / 4);
        float half = LaserHum.pitchAt(LaserHum.GLIDE_TICKS / 2);
        assertTrue(LaserHum.START_PITCH - quarter > quarter - half, "the glide does not ease out");
    }
}
