package com.mercuriusxeno.goo.client.ber;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A marker's visual reaches as far as its box or a sized black hole's pull,
 * whichever is wider, so a hole dragged huge is neither culled off screen
 * nor out of view distance (decision black-hole-leaves-a-compression-sphere).
 */
class AbilityBlockRendererTest {

    private static final double DELTA = 1e-9;

    @Test
    void anUnsizedMarkerReachesItsBox() {
        assertEquals(AbilityBlockRenderer.RENDER_BOX_HALF_EXTENT, AbilityBlockRenderer.visualReach(0), DELTA);
        assertEquals(AbilityBlockRenderer.RENDER_BOX_HALF_EXTENT, AbilityBlockRenderer.visualReach(3), DELTA);
    }

    @Test
    void aHugeHoleReachesItsPull() {
        assertEquals(3 * 25 + 0.5, AbilityBlockRenderer.visualReach(25), DELTA);
    }
}
