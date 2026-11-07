package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A flying chunk is lit by the brighter of its block's light and the glove's,
 * block light and sky light each taken on its own, so a chunk passing through
 * a solid block never reads black
 * (decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand).
 */
class DrinkPeelsTest {

    private static final int SKY_SHIFT = 16;

    private static int light(int block, int sky) {
        return block | sky << SKY_SHIFT;
    }

    @Test
    void theBrighterLightTakesEachHalfFromWhicheverIsBrighterThere() {
        assertEquals(light(240, 160), DrinkPeels.brighterOf(light(240, 0), light(32, 160)));
    }

    @Test
    void aDarkLightLeavesTheOtherAsItIs() {
        assertEquals(light(48, 240), DrinkPeels.brighterOf(0, light(48, 240)));
    }
}
