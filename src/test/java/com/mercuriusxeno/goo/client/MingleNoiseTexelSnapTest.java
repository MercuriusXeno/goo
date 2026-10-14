package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mingle field samples at texel resolution: one value per sixteenth-block
 * texel in world space, one per sprite pixel on a melting item, and
 * crucible_dissolve.fsh reads it through mingle_noise.glsl's snap
 * (decision mingle-noise-snaps-to-texels). Shaders are read through the classpath.
 */
class MingleNoiseTexelSnapTest {

    private static final String SHADERS = "/assets/goo/shaders/core/";
    /** A world-space cell count, a cell four texels across. */
    private static final float WORLD_CELLS_PER_BLOCK = 4f;
    /** crucible_dissolve.fsh's DISSOLVE_CELLS_PER_PIXEL. */
    private static final float DISSOLVE_CELLS_PER_PIXEL = 0.25f;
    private static final float ATLAS_PIXELS = 1024f;

    private static String shader(String name) throws IOException {
        try (InputStream in = MingleNoiseTexelSnapTest.class.getResourceAsStream(SHADERS + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Nested
    class FieldPerTexel {

        @Test
        void twoPointsInsideOneTexelSampleOneValue() {
            float first = MingleNoiseModel.texelField(3.005f, 64.005f, -7.005f, WORLD_CELLS_PER_BLOCK);
            float second = MingleNoiseModel.texelField(3.035f, 64.035f, -7.035f, WORLD_CELLS_PER_BLOCK);

            assertEquals(first, second);
        }

        @Test
        void neighboringTexelsSampleDistinctValues() {
            float before = MingleNoiseModel.texelField(3.06f, 64.005f, -7.005f, WORLD_CELLS_PER_BLOCK);
            float after = MingleNoiseModel.texelField(3.065f, 64.005f, -7.005f, WORLD_CELLS_PER_BLOCK);

            assertNotEquals(before, after);
        }

        @Test
        void aPointSnapsToItsTexelsMiddle() {
            assertEquals(3.03125f, MingleNoiseModel.texelSnap(3.01f, MingleNoiseModel.WORLD_TEXELS));
            assertEquals(-7.03125f, MingleNoiseModel.texelSnap(-7.01f, MingleNoiseModel.WORLD_TEXELS));
        }
    }

    @Nested
    class FieldPerItemPixel {

        @Test
        void twoPointsInsideOneSpritePixelSampleOneValue() {
            float first = MingleNoiseModel.itemPixelField(
                200.1f / ATLAS_PIXELS, 37.2f / ATLAS_PIXELS, ATLAS_PIXELS, ATLAS_PIXELS, DISSOLVE_CELLS_PER_PIXEL);
            float second = MingleNoiseModel.itemPixelField(
                200.9f / ATLAS_PIXELS, 37.8f / ATLAS_PIXELS, ATLAS_PIXELS, ATLAS_PIXELS, DISSOLVE_CELLS_PER_PIXEL);

            assertEquals(first, second);
        }

        @Test
        void neighboringSpritePixelsSampleDistinctValues() {
            float before = MingleNoiseModel.itemPixelField(
                200.9f / ATLAS_PIXELS, 37.5f / ATLAS_PIXELS, ATLAS_PIXELS, ATLAS_PIXELS, DISSOLVE_CELLS_PER_PIXEL);
            float after = MingleNoiseModel.itemPixelField(
                201.1f / ATLAS_PIXELS, 37.5f / ATLAS_PIXELS, ATLAS_PIXELS, ATLAS_PIXELS, DISSOLVE_CELLS_PER_PIXEL);

            assertNotEquals(before, after);
        }
    }

    @Nested
    class CrucibleDissolveShader {

        @Test
        void mingleNoiseDefinesTheSnapAtSixteenTexelsPerBlock() throws IOException {
            String noise = shader("mingle_noise.glsl");

            assertTrue(noise.contains("const float MINGLE_WORLD_TEXELS = 16.0;"), "the world texel");
            assertTrue(noise.contains("vec3 mingleTexelSnap(vec3 p, float texelsPerUnit) {\n"
                + "    return (floor(p * texelsPerUnit) + 0.5) / texelsPerUnit;"), "the world snap");
            assertTrue(noise.contains("vec2 mingleTexelSnap(vec2 p, vec2 texelsPerUnit) {\n"
                + "    return (floor(p * texelsPerUnit) + 0.5) / texelsPerUnit;"), "the sprite snap");
        }

        @Test
        void theDissolveSnapsToTheItemsPixelBeforeTheFieldAndThePick() throws IOException {
            String dissolve = shader("crucible_dissolve.fsh");
            int snapAt = dissolve.indexOf(
                "vec3 itemPixel = vec3(mingleTexelSnap(texCoord0, atlasPixels) * atlasPixels, 0.0);");
            int fieldAt = dissolve.indexOf("mingleField(itemPixel * DISSOLVE_CELLS_PER_PIXEL");
            int pickAt = dissolve.indexOf("mingleOpacity(itemPixel * PICK_SCALE");

            assertTrue(dissolve.contains("vec2 atlasPixels = vec2(textureSize(Sampler0, 0));"), "the atlas size");
            assertTrue(snapAt >= 0, "the snap");
            assertTrue(fieldAt > snapAt, "the field samples the snapped pixel");
            assertTrue(pickAt > snapAt, "the type pick samples the snapped pixel");
        }
    }
}
