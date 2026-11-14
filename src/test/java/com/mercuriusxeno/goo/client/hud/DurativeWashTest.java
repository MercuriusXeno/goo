package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wash a durative self ability lays over the view as its use lands:
 * nothing as it begins, rising to cover the viewport, and gone again by its
 * named duration's end.
 * durative-self-abilities-wash-over-the-view-in-place-of-eating
 */
class DurativeWashTest {

    private static final int SAMPLES = 48;
    private static final float DELTA = 1e-6f;

    @Test
    void theWashIsNothingAtItsStartAndItsEnd() {
        assertEquals(0f, DurativeWash.strengthAt(0), DELTA);
        assertEquals(0f, DurativeWash.strengthAt(DurativeWash.WASH_TICKS), DELTA);
    }

    @Test
    void betweenItsStartAndItsEndTheWashCoversTheView() {
        for (int sample = 1; sample < SAMPLES; sample++) {
            double ticks = (double) DurativeWash.WASH_TICKS * sample / SAMPLES;

            assertTrue(DurativeWash.strengthAt(ticks) > 0f, "the wash should show at tick " + ticks);
        }
    }

    @Test
    void theWashRisesToItsFullStrengthAndFallsBack() {
        float peak = 0f;
        for (int sample = 0; sample <= SAMPLES; sample++) {
            peak = Math.max(peak, DurativeWash.strengthAt((double) DurativeWash.WASH_TICKS * sample / SAMPLES));
        }

        assertEquals(1f, peak, 0.05f, "the wash should reach its full strength");
    }

    @Test
    void pastItsEndTheWashIsGone() {
        assertEquals(0f, DurativeWash.strengthAt(DurativeWash.WASH_TICKS + 1.0), DELTA);
    }
}
