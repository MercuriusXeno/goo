package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.DegradeStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An exposure's mingled block draws in its own colors untinted, and in the
 * tint it carries, opaque, where an ability sent one
 * (decision decay-gnats-degrade-each-block-once).
 */
class BlockMingleRendererTest {

    @Test
    void anUntintedExposureDrawsInItsOwnColors() {
        assertEquals(0xFFFFFFFF, BlockMingleRenderer.mingleColor(-1));
    }

    @Test
    void aTintedExposureDrawsInItsTintOpaque() {
        assertEquals(0xFF000000 | DegradeStep.NETHER_MAROON,
                BlockMingleRenderer.mingleColor(DegradeStep.NETHER_MAROON));
    }
}
