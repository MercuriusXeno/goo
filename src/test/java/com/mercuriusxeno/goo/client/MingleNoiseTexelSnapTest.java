package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Nested
    class BlockMingleShader {

        @Test
        void theFaceTexelSnapsAtTheWorldTexel() throws IOException {
            String noise = shader("mingle_noise.glsl");
            int faceTexelAt = noise.indexOf("vec3 mingleFaceTexel(vec3 worldPos) {");

            assertTrue(faceTexelAt >= 0, "the face texel");
            assertTrue(noise.indexOf("return mingleTexelSnap(worldPos - faceNormal * (0.25 / MINGLE_WORLD_TEXELS), "
                + "MINGLE_WORLD_TEXELS);", faceTexelAt) > faceTexelAt, "the face texel snaps at the world texel");
        }

        @Test
        void theTransformSnapsItsPositionBeforeTheField() throws IOException {
            String mingle = shader("block_mingle.fsh");
            int snapAt = mingle.indexOf("vec3 texelPos = mingleFaceTexel(mingleWorldPos);");
            int fieldAt = mingle.indexOf(
                "float field = mingleField(texelPos * MINGLE_BLOCK_CELLS + mingleSeed * MINGLE_SEED_OFFSET);");

            assertTrue(snapAt >= 0, "the snap");
            assertTrue(fieldAt > snapAt, "the field samples the snapped position");
            assertEquals(2, mingle.split("mingleWorldPos", -1).length - 1,
                "mingleWorldPos is declared and read only by the snap");
        }
    }

    @Nested
    class FluidSurfaceAndCoatShaders {

        @Test
        void theSurfaceMingleSamplesTheWorldTexel() throws IOException {
            String surface = shader("goo_fluid_surface.fsh");

            assertTrue(surface.contains(
                "float opacity = mingleOpacity(mingleFaceTexel(mingleWorldPos), GameTime, shareSeed.x, shareSeed.y);"),
                "the surface's opacity samples the snapped position");
            assertEquals(2, surface.split("mingleWorldPos", -1).length - 1,
                "mingleWorldPos is declared and read only by the snap");
        }

        @Test
        void theCoatEdgeSamplesTheWorldTexel() throws IOException {
            String coat = shader("goo_mob_coat.fsh");
            int reachAt = coat.indexOf("float splatReach(vec3 offset) {\n    offset = mingleFaceTexel(offset);");
            int noiseAt = coat.indexOf("mingleValueNoise(offset * EDGE_CELLS_PER_BLOCK)");
            int distanceAt = coat.indexOf("return splatDistance(offset) + EDGE_NOISE * 2.0 * noise;");

            assertTrue(reachAt >= 0, "the reach snaps its offset first");
            assertTrue(noiseAt > reachAt, "the edge noise samples the snapped offset");
            assertTrue(distanceAt > reachAt, "the edge's reach is read per texel");
        }

        @Test
        void theModelsOpacityIsOneValuePerTexel() {
            float first = MingleNoiseModel.opacity(3.005f, 64.5f, -7.005f, 0.3f, 0.5f, 1f);
            float second = MingleNoiseModel.opacity(3.035f, 64.53f, -7.035f, 0.3f, 0.5f, 1f);

            assertEquals(first, second);
        }
    }

    @Nested
    class SkinShaders {

        private static final String SKIN_SNAP = "vec2 skinTexel = mingleTexelSnap(skinCoord, MINGLE_SKIN_TEXELS);";
        private static final Pattern MINGLE_CALL = Pattern.compile("mingle(?:Field|ValueNoise)\\(([^;]*)");

        /** Every mingle call in main takes the snapped skin texel, never the raw skin coordinate. */
        private void assertEveryMingleCallInMainIsSnapped(String name) throws IOException {
            String source = shader(name);
            String main = source.substring(source.indexOf("void main() {"));
            int snapAt = main.indexOf(SKIN_SNAP);
            Matcher call = MINGLE_CALL.matcher(main);
            int calls = 0;

            assertTrue(snapAt >= 0, name + " snaps skinCoord at the skin's texel");
            while (call.find()) {
                calls++;
                assertTrue(call.start() > snapAt, name + " samples before the snap: " + call.group());
                assertTrue(call.group(1).contains("skinTexel") && !call.group(1).contains("skinCoord"),
                    name + " samples the raw coordinate: " + call.group());
            }
            assertTrue(calls > 0, name + " holds a mingle call");
        }

        @Test
        void theSkinTexelIsTheSixtyFourTexelSkin() throws IOException {
            assertTrue(shader("mingle_noise.glsl").contains("const vec2 MINGLE_SKIN_TEXELS = vec2(64.0);"));
        }

        @Test
        void petrifyStoneSamplesTheSkinTexel() throws IOException {
            assertEveryMingleCallInMainIsSnapped("petrify_stone.fsh");
        }

        @Test
        void vineTangleSamplesTheSkinTexel() throws IOException {
            assertEveryMingleCallInMainIsSnapped("vine_tangle.fsh");
        }

        @Test
        void theAilmentOverlaySamplesTheSkinTexel() throws IOException {
            String overlay = shader("goo_ailment_overlay.fsh");

            assertEveryMingleCallInMainIsSnapped("goo_ailment_overlay.fsh");
            assertTrue(overlay.contains("shine = shimmerField(skinTexel, GameTime);"), "the shimmer's noise");
            assertTrue(overlay.contains("shine = writheField(skinTexel, GameTime);"), "the writhe's noise");
        }
    }
}
