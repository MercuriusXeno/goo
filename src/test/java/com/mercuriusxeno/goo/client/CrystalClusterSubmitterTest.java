package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The crystal's quads map the growing type's sprite rectangle whole and upright
 * (decision crystallizer-emits-chrysm).
 */
class CrystalClusterSubmitterTest {

    @Test
    void eachQuadMapsTheWholeSpriteUpright() {
        GooRenderUtil.UvRect sprite = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);
        float[][] corners = CrystalClusterSubmitter.quadUv(sprite);
        assertArrayEquals(new float[] {0.25f, 0.625f}, corners[0], "bottom left");
        assertArrayEquals(new float[] {0.375f, 0.625f}, corners[1], "bottom right");
        assertArrayEquals(new float[] {0.375f, 0.5f}, corners[2], "top right");
        assertArrayEquals(new float[] {0.25f, 0.5f}, corners[3], "top left");
    }
}
