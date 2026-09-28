package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The crystal's quads tile the growing type's sprite at one texture pixel per model
 * pixel rather than stretching it (decision crystallizer-emits-chrysm).
 */
class CrystalClusterSubmitterTest {

    @Test
    void aFaceTilesAtOneTexturePixelPerModelPixel() {
        GooRenderUtil.UvRect sprite = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);
        float[][] corners = CrystalClusterSubmitter.quadUv(sprite, 4, 8);
        assertArrayEquals(new float[] {0.25f, 0.625f}, corners[0], "bottom left");
        assertArrayEquals(new float[] {0.28125f, 0.625f}, corners[1], "bottom right, 4 of 16 pixels across");
        assertArrayEquals(new float[] {0.28125f, 0.5625f}, corners[2], "top right, 8 of 16 pixels up");
        assertArrayEquals(new float[] {0.25f, 0.5625f}, corners[3], "top left");
    }

    @Test
    void aFaceNeverStretchesPastOneSprite() {
        GooRenderUtil.UvRect sprite = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);
        float[][] corners = CrystalClusterSubmitter.quadUv(sprite, 20, 20);
        assertArrayEquals(new float[] {0.375f, 0.5f}, corners[2], "a 20 pixel face holds at the sprite's edge");
    }
}
