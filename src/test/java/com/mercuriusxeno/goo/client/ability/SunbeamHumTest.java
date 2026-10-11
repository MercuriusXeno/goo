package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SunbeamHum's pitch: it starts pitched up as the hold begins, glides down
 * fast then slow, and settles at the low thrum it holds after.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
class SunbeamHumTest {

    private static final float TOLERANCE = 1e-6f;

    @Test
    void startsPitchedUp() {
        assertEquals(SunbeamHum.START_PITCH, SunbeamHum.pitchAt(0), TOLERANCE);
    }

    @Test
    void settlesAtTheThrumAndHolds() {
        assertEquals(SunbeamHum.THRUM_PITCH, SunbeamHum.pitchAt(SunbeamHum.GLIDE_TICKS), TOLERANCE);
        assertEquals(SunbeamHum.THRUM_PITCH, SunbeamHum.pitchAt(SunbeamHum.GLIDE_TICKS * 10), TOLERANCE);
    }

    @Test
    void glidesDownFastThenSlow() {
        float quarter = SunbeamHum.pitchAt(SunbeamHum.GLIDE_TICKS / 4);
        float half = SunbeamHum.pitchAt(SunbeamHum.GLIDE_TICKS / 2);
        assertTrue(SunbeamHum.START_PITCH - quarter > quarter - half, "the glide does not ease out");
    }
}
