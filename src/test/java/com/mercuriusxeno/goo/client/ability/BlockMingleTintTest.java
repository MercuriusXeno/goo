package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block mingling through a transform keeps its biome tint: a tinted quad wears
 * its tint source's color under the mingle's own tint, an untinted quad the
 * mingle's tint alone.
 */
class BlockMingleTintTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRASS_GREEN = 0x79C05A;
    private static final int MAROON = 0xFF800000;

    @Test
    void aTintedQuadWearsItsBiomeColor() {
        assertEquals(0xFF000000 | GRASS_GREEN, BlockMingleRenderer.quadColor(WHITE, 0, index -> GRASS_GREEN),
            "the tinted quad drops its biome color");
    }

    @Test
    void anUntintedQuadWearsTheMinglesTintAlone() {
        assertEquals(MAROON, BlockMingleRenderer.quadColor(MAROON, -1, index -> GRASS_GREEN));
    }
}
