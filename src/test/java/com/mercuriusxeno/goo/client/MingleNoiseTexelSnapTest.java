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
    class SnapHelpers {

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
        void theFaceTexelStepsOffTheFaceThenSnapsAtTheWorldTexel() throws IOException {
            String noise = shader("mingle_noise.glsl");

            assertTrue(noise.contains("return worldPos - faceNormal * (0.25 / MINGLE_WORLD_TEXELS);"), "the face step");
            assertTrue(noise.contains("vec3 mingleFaceTexel(vec3 worldPos) {\n"
                + "    return mingleTexelSnap(mingleOffFace(worldPos), MINGLE_WORLD_TEXELS);"), "the face snap");
        }

        @Test
        void theModelsTexelsAreTheShaders() throws IOException {
            String noise = shader("mingle_noise.glsl");

            assertTrue(noise.contains("const float MINGLE_WORLD_TEXELS = " + MingleNoiseModel.WORLD_TEXELS + ";"));
            assertTrue(noise.contains("const vec2 MINGLE_SKIN_TEXELS = vec2(" + MingleNoiseModel.SKIN_TEXELS + ");"));
        }

        @Test
        void theCrucibleGlowPickKeepsTheSnap() throws IOException {
            String dissolve = shader("crucible_dissolve.fsh");
            int snapAt = dissolve.indexOf(
                "vec3 itemPixel = vec3(mingleTexelSnap(texCoord0, atlasPixels) * atlasPixels, 0.0);");

            assertTrue(snapAt >= 0, "the snap");
            assertTrue(dissolve.indexOf("mingleOpacity(itemPixel * PICK_SCALE") > snapAt,
                "the type pick samples the snapped pixel");
        }
    }

    /** The cut-edge five blend corner-sampled noise per pixel (decision cut-edge-blends-texel-corners). */
    @Nested
    class CornerBlend {

        private static final float[] NO_OFFSET = {0f, 0f, 0f};
        private static final float[] SEEDED = {37.1f, 11.7f, 53.3f};
        private static final float CELLS = 4f;
        private static final float HAIR = 1e-4f;
        private static final float EDGE_AGREEMENT = 1e-3f;

        private int indexAfter(String source, String text, int from) {
            int at = source.indexOf(text, from);
            assertTrue(at >= 0, "missing: " + text);
            return at;
        }

        @Test
        void mingleNoiseDefinesTheEaseAndTheCornerBlend() throws IOException {
            String noise = shader("mingle_noise.glsl");

            assertTrue(noise.contains("const float MINGLE_CORNER_EASE = "), "the artist's dial");
            assertTrue(noise.contains("return mix(fraction, fraction * fraction * (3.0 - 2.0 * fraction), "
                + "MINGLE_CORNER_EASE);"), "the weight slides from slant to curve");
            assertTrue(noise.contains("float mingleCornerField(vec2 p, vec2 texelsPerUnit, float cellsPerUnit, "
                + "vec3 offset) {"), "the sprite and skin blend");
            assertTrue(noise.contains("vec3 grid = mingleOffFace(worldPos) * MINGLE_WORLD_TEXELS;"),
                "the world blend steps off the face");
        }

        @Test
        void theCrucibleDissolveThresholdsTheCornerBlend() throws IOException {
            String dissolve = shader("crucible_dissolve.fsh");
            int fieldAt = indexAfter(dissolve,
                "float field = mingleCornerField(texCoord0 * atlasPixels, vec2(1.0), DISSOLVE_CELLS_PER_PIXEL,", 0);

            assertTrue(indexAfter(dissolve, "if (field < threshold)", fieldAt) > fieldAt);
            assertTrue(!dissolve.contains("mingleField("), "the dissolve still reads the plain field instead of the corner blend");
        }

        @Test
        void theBlockTransformThresholdsTheCornerBlend() throws IOException {
            String mingle = shader("block_mingle.fsh");
            int fieldAt = indexAfter(mingle,
                "float field = mingleWorldCornerField(mingleWorldPos, MINGLE_BLOCK_CELLS, mingleSeed * MINGLE_SEED_OFFSET);",
                0);

            assertTrue(indexAfter(mingle, "if (field < threshold)", fieldAt) > fieldAt);
            assertTrue(!mingle.contains("mingleTexelSnap") && !mingle.contains("mingleFaceTexel"),
                "the transform still snaps to texel centres instead of blending corners");
        }

        @Test
        void petrifyAndVinesThresholdTheCornerBlendAtTheSkinTexel() throws IOException {
            String stone = shader("petrify_stone.fsh");
            String vine = shader("vine_tangle.fsh");
            int stoneAt = indexAfter(stone, "float field = mingleCornerField(skinCoord, MINGLE_SKIN_TEXELS, PATCH_CELLS,",
                0);

            assertTrue(indexAfter(stone, "if (field < threshold)", stoneAt) > stoneAt);
            assertTrue(vine.contains("mingleCornerField(skinCoord, MINGLE_SKIN_TEXELS, PATCH_CELLS, vec3(0.0)) "
                + "< mingleThreshold(share)"), "the tangle thresholds the blend");
            assertTrue(!stone.contains("mingleTexelSnap") && !vine.contains("mingleTexelSnap"),
                "a skin shader still snaps to texel centres instead of blending corners");
        }

        @Test
        void theCoatEdgeBlendsItsNoiseAtTheCorners() throws IOException {
            String coat = shader("goo_mob_coat.fsh");

            assertTrue(coat.contains("float splatReach(vec3 offset) {\n"
                + "    float noise = mingleWorldCornerNoise(offset, EDGE_CELLS_PER_BLOCK) - 0.5;\n"
                + "    return splatDistance(offset) + EDGE_NOISE * 2.0 * noise;"), "the edge noise is corner-blended");
            assertTrue(!coat.contains("mingleFaceTexel"), "the coat edge still snaps to texel centres instead of blending corners");
        }

        @Test
        void theSurfaceAndTheOverlayKeepThePlainSnap() throws IOException {
            String surface = shader("goo_fluid_surface.fsh");
            assertTrue(surface.contains("vec3 faceTexel = mingleFaceTexel(mingleWorldPos);"),
                "the surface no longer snaps its mingle position to the world texel");
            assertTrue(surface.contains("float opacity = mingleOpacity(faceTexel, GameTime, share, seed);"),
                "the surface's opacity no longer reads the snapped texel");
            String overlay = shader("goo_ailment_overlay.fsh");
            assertTrue(overlay.contains("vec2 skinTexel = mingleTexelSnap(skinCoord, MINGLE_SKIN_TEXELS);"));
            assertTrue(!overlay.contains("mingleCornerField"), "the overlay blends corners where it should keep the plain snap");
        }

        @Test
        void aPointOnACornerTakesThatCornersValueAtSlantAndCurve() {
            float corner = MingleNoiseModel.fieldAt(200f * CELLS / 64f + SEEDED[0], 37f * CELLS / 64f + SEEDED[1],
                SEEDED[2]);

            assertEquals(corner, MingleNoiseModel.cornerField(200f / 64f, 37f / 64f, 64f, CELLS, SEEDED, 0f), 1e-6f);
            assertEquals(corner, MingleNoiseModel.cornerField(200f / 64f, 37f / 64f, 64f, CELLS, SEEDED, 1f), 1e-6f);
        }

        @Test
        void twoPointsInsideOneTexelTakeDistinctBlendedValues() {
            float near = MingleNoiseModel.cornerField(200.2f / 64f, 37.3f / 64f, 64f, CELLS, NO_OFFSET, 1f);
            float far = MingleNoiseModel.cornerField(200.7f / 64f, 37.8f / 64f, 64f, CELLS, NO_OFFSET, 1f);

            assertNotEquals(near, far);
        }

        @Test
        void aSharedTexelEdgeAgreesFromBothSides() {
            for (float ease : new float[] {0f, 1f}) {
                float before = MingleNoiseModel.cornerField((201f - HAIR) / 64f, 37.4f / 64f, 64f, CELLS, NO_OFFSET,
                    ease);
                float after = MingleNoiseModel.cornerField((201f + HAIR) / 64f, 37.4f / 64f, 64f, CELLS, NO_OFFSET,
                    ease);

                assertEquals(before, after, EDGE_AGREEMENT, "the edge parts at ease " + ease);
            }
        }

        @Test
        void theWorldBlendTakesItsCornerAndAgreesAcrossAnEdge() {
            float corner = MingleNoiseModel.fieldAt(48f / 16f * CELLS, 1024f / 16f * CELLS, -112f / 16f * CELLS);

            assertEquals(corner, MingleNoiseModel.worldCornerField(3f, 64f, -7f, CELLS, NO_OFFSET, 0f), 1e-5f);
            for (float ease : new float[] {0f, 1f}) {
                float before = MingleNoiseModel.worldCornerField(3.0625f - HAIR, 64.02f, -7.01f, CELLS, NO_OFFSET, ease);
                float after = MingleNoiseModel.worldCornerField(3.0625f + HAIR, 64.02f, -7.01f, CELLS, NO_OFFSET, ease);

                assertEquals(before, after, EDGE_AGREEMENT, "the world edge parts at ease " + ease);
            }
        }
    }

    @Nested
    class FluidSurfaceShader {

        @Test
        void theModelsOpacityIsOneValuePerTexel() {
            float first = MingleNoiseModel.opacity(3.005f, 64.5f, -7.005f, 0.3f, 0.5f, 1f);
            float second = MingleNoiseModel.opacity(3.035f, 64.53f, -7.035f, 0.3f, 0.5f, 1f);

            assertEquals(first, second);
        }
    }

    @Nested
    class AilmentOverlayShader {

        private static final Pattern MINGLE_CALL = Pattern.compile("mingle(?:Field|ValueNoise)\\(([^;]*)");

        @Test
        void everyNoiseCallInMainTakesTheSnappedSkinTexel() throws IOException {
            String source = shader("goo_ailment_overlay.fsh");
            String main = source.substring(source.indexOf("void main() {"));
            int snapAt = main.indexOf("vec2 skinTexel = mingleTexelSnap(skinCoord, MINGLE_SKIN_TEXELS);");
            Matcher call = MINGLE_CALL.matcher(main);
            int calls = 0;

            assertTrue(snapAt >= 0, "the overlay snaps skinCoord at the skin's texel");
            while (call.find()) {
                calls++;
                assertTrue(call.start() > snapAt, "samples before the snap: " + call.group());
                assertTrue(call.group(1).contains("skinTexel") && !call.group(1).contains("skinCoord"),
                    "samples the raw coordinate: " + call.group());
            }
            assertTrue(calls > 0, "the overlay holds a noise call");
            assertTrue(source.contains("shine = shimmerField(skinTexel, GameTime);"), "the shimmer's noise");        }
    }
}
