package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A phased player's held item draws each quad in its own color, tinted or
 * white, under the fainter alpha the player's own look takes.
 * phased-things-draw-as-a-ghostly-image
 */
class PhasedHandsTest {

    private static final int GRASS_TINT = 0xFF79C05A;
    private static final int PHASED_WHITE = PhaseLook.OWN_PHASED_ALPHA << 24 | 0xFFFFFF;
    private static final int PHASED_GRASS = PhaseLook.OWN_PHASED_ALPHA << 24 | 0x79C05A;

    @Test
    void aHeldItemsQuadKeepsItsColorUnderThePhaseAlpha() {
        int[] tints = {GRASS_TINT};
        assertEquals(Map.of("untinted", PHASED_WHITE, "tinted", PHASED_GRASS, "tint the item lacks", PHASED_WHITE),
                Map.of("untinted", PhasedHands.fadedColor(tints, PhasedHands.UNTINTED),
                        "tinted", PhasedHands.fadedColor(tints, 0),
                        "tint the item lacks", PhasedHands.fadedColor(tints, 1)));
    }
}
