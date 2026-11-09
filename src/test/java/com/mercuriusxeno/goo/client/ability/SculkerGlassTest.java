package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sculker's glass field shows a block's own texture at full strength at the
 * player, weaker across the field and not at all past its radius
 * (decision sculker-shows-blocks-translucent-close-up).
 */
class SculkerGlassTest {

    private static final double CLOSE = 1e-6;

    @Test
    void fullStrengthAtThePlayer() {
        assertEquals(1f, SculkerGlass.strengthAt(0), CLOSE);
    }

    @Test
    void fallsAcrossTheField() {
        float near = SculkerGlass.strengthAt(SculkerGlass.FIELD_RADIUS * 0.25);
        float far = SculkerGlass.strengthAt(SculkerGlass.FIELD_RADIUS * 0.75);
        assertTrue(near > far && far > 0, "strength should fall from " + near + " to " + far + " and stay above zero");
    }

    @Test
    void zeroAtTheRadiusAndPastIt() {
        assertEquals(0f, SculkerGlass.strengthAt(SculkerGlass.FIELD_RADIUS), CLOSE);
        assertEquals(0f, SculkerGlass.strengthAt(SculkerGlass.FIELD_RADIUS + 3), CLOSE);
    }
}
