package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A phased player's own hearts draw as vanilla's heart in blue and
 * see-through, whole for whole and half for half, in place of the red.
 * phased-things-draw-as-a-ghostly-image
 */
class PhasedHeartsTest {

    @Test
    void aPhasedHeartDrawsThePhasedSpriteWholeOrHalf() {
        assertEquals(List.of("goo:hud/heart/phased_full", "goo:hud/heart/phased_half"),
                List.of(PhasedHearts.sprite(false).toString(), PhasedHearts.sprite(true).toString()));
    }
}
