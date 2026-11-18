package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the outline a led mob wears: opaque red, its brightness pulsing.
 */
class LeadOutlineTest {

    private static final int OPAQUE = 0xFF;

    @Test
    void outlineIsOpaqueRed() {
        for (float gameTime = 0f; gameTime < LeadOutline.PULSE_PERIOD; gameTime += 1f) {
            int color = LeadOutline.color(gameTime);
            float at = gameTime;
            assertAll("at " + at,
                    () -> assertEquals(OPAQUE, ARGB.alpha(color)),
                    () -> assertTrue(ARGB.red(color) > 0),
                    () -> assertEquals(0, ARGB.green(color)),
                    () -> assertEquals(0, ARGB.blue(color)));
        }
    }

    @Test
    void outlineBrightnessPulses() {
        assertNotEquals(ARGB.red(LeadOutline.color(0f)),
                ARGB.red(LeadOutline.color(LeadOutline.PULSE_PERIOD / 2f)));
    }
}
