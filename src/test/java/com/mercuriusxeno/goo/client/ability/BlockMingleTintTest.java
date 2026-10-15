package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.QuadInstance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block mingling through a transform keeps the colors the world lit it with:
 * an untinted transform leaves each vertex's baked shade, occlusion and biome
 * tint as it is, a tinted one multiplies its tint over them, and every quad
 * carries the transform's overlay.
 */
class BlockMingleTintTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int SHADED_GRASS = 0xFF61994B;
    private static final int MAROON = 0xFF800000;
    private static final int OVERLAY = 2048 | 200 << 16;
    private static final int VERTICES = 4;

    private static QuadInstance litQuad() {
        QuadInstance instance = new QuadInstance();
        instance.setColor(SHADED_GRASS);
        return instance;
    }

    @Test
    void anUntintedTransformKeepsTheWorldsBakedColors() {
        QuadInstance instance = litQuad();

        BlockMingleRenderer.dressQuad(instance, WHITE, OVERLAY);

        for (int vertex = 0; vertex < VERTICES; vertex++) {
            assertEquals(SHADED_GRASS, instance.getColor(vertex));
        }
        assertEquals(OVERLAY, instance.overlayCoords());
    }

    @Test
    void aTintedTransformMultipliesItsTintOverTheWorldsColors() {
        QuadInstance instance = litQuad();

        BlockMingleRenderer.dressQuad(instance, MAROON, OVERLAY);

        assertEquals(net.minecraft.util.ARGB.multiply(SHADED_GRASS, MAROON), instance.getColor(0));
    }
}
