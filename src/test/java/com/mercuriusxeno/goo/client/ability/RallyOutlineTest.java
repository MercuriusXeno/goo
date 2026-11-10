package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the outline a rallied mob wears: opaque red, its brightness pulsing.
 */
class RallyOutlineTest {

    private static final int OPAQUE = 0xFF;

    @Test
    void outlineIsOpaqueRed() {
        for (float gameTime = 0f; gameTime < RallyOutline.PULSE_PERIOD; gameTime += 1f) {
            int color = RallyOutline.color(gameTime);
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
        assertNotEquals(ARGB.red(RallyOutline.color(0f)),
                ARGB.red(RallyOutline.color(RallyOutline.PULSE_PERIOD / 2f)));
    }
}
