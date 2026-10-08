package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.CuboidBounds;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A whole goo copy fills what it copies, and a melting one slumps lower,
 * spreads its foot and rounds its top (decision unmake-waves-dissolve-by-crucible-cost).
 */
class GooSagTest {

    private static final float DELTA = 1e-5f;

    @Test
    void aWholeCopyFillsTheBlock() {
        List<CuboidBounds> layers = GooSag.layers(1f, 1f, 1f, 0f);

        for (CuboidBounds layer : layers) {
            assertEquals(0f, layer.x0(), DELTA);
            assertEquals(1f, layer.x1(), DELTA);
        }
        assertEquals(1f, layers.getLast().yTop(), DELTA);
    }

    @Test
    void aMeltedCopySlumpsSpreadsAndRounds() {
        List<CuboidBounds> layers = GooSag.layers(1f, 1f, 1f, 1f);

        assertEquals(1f - GooSag.SLUMP, layers.getLast().yTop(), DELTA);
        assertTrue(layers.getFirst().x1() - layers.getFirst().x0() > 1f);
        assertTrue(layers.getLast().x1() - layers.getLast().x0() < layers.getFirst().x1() - layers.getFirst().x0());
    }
}
