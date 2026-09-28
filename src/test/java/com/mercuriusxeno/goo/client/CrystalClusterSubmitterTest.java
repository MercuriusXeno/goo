package com.mercuriusxeno.goo.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystal's faces tile the growing type's sprite by their place in the block, one
 * texture pixel per model pixel, each face showing its own part of the sprite
 * (decision crystallizer-emits-chrysm).
 */
class CrystalClusterSubmitterTest {

    private static final GooRenderUtil.UvRect SPRITE = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);
    private static final Vec3 FACING_Z = new Vec3(0, 0, 1);

    @Test
    void aFaceReadsItsPlaceInTheBlockAtOnePixelPerPixel() {
        Vec3[] face = {new Vec3(4, 20, 8), new Vec3(6, 20, 8), new Vec3(6, 24, 8), new Vec3(4, 24, 8)};
        float[][] uv = CrystalClusterSubmitter.blockUv(SPRITE, face, FACING_Z);
        float pixel = (0.375f - 0.25f) / 16;
        assertEquals(0.25f + 4 * pixel, uv[0][0], 1e-6, "u reads x = 4");
        assertEquals(0.25f + 6 * pixel, uv[1][0], 1e-6, "u reads x = 6");
        assertEquals(0.5f + 12 * pixel, uv[0][1], 1e-6, "v reads 12 pixels down from the top");
        assertEquals(0.5f + 8 * pixel, uv[2][1], 1e-6, "v reads 8 pixels down from the top");
    }

    @Test
    void facesAtDifferentPlacesReadDifferentPartsOfTheSprite() {
        Vec3[] left = {new Vec3(2, 18, 8), new Vec3(4, 18, 8), new Vec3(4, 22, 8), new Vec3(2, 22, 8)};
        Vec3[] right = {new Vec3(10, 18, 8), new Vec3(12, 18, 8), new Vec3(12, 22, 8), new Vec3(10, 22, 8)};
        float leftU = CrystalClusterSubmitter.blockUv(SPRITE, left, FACING_Z)[0][0];
        float rightU = CrystalClusterSubmitter.blockUv(SPRITE, right, FACING_Z)[0][0];
        assertNotEquals(leftU, rightU, "two faces apart should not show the same sliver");
    }

    @Test
    void aFacePastTheBlockEdgeStaysWithinOneSprite() {
        Vec3[] face = {new Vec3(-3, 18, 8), new Vec3(-1, 18, 8), new Vec3(-1, 22, 8), new Vec3(-3, 22, 8)};
        for (float[] corner : CrystalClusterSubmitter.blockUv(SPRITE, face, FACING_Z)) {
            assertTrue(corner[0] >= 0.25f && corner[0] <= 0.375f && corner[1] >= 0.5f && corner[1] <= 0.625f,
                    "corner " + corner[0] + ", " + corner[1] + " should stay on the sprite");
        }
    }
}
