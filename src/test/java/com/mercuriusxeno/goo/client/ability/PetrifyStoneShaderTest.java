package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.MingleNoiseModel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Petrify's stone, and frost's ice through the same shader, draws one stone
 * texel per skin texel along either axis on a skin of any size, and its patch
 * edges read the skin's real texel grid
 * (decision diagnose-and-correct-petrify-stone-stretching-off-native-texels).
 * Shaders are read through the classpath.
 */
class PetrifyStoneShaderTest {

    private static final String SHADERS = "/assets/goo/shaders/core/";
    /** Vanilla's stone and packed ice, 16 texels on a side. */
    private static final int STONE_SIZE = 16;
    private static final float TEXEL_AGREEMENT = 1e-4f;

    private static String shader(String name) throws IOException {
        try (InputStream in = PetrifyStoneShaderTest.class.getResourceAsStream(SHADERS + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Nested
    class StoneTexelPerSkinTexel {

        @ParameterizedTest(name = "a {0} by {1} skin")
        @CsvSource({"64, 64", "64, 32", "128, 64"})
        void oneSkinTexelAlongEitherAxisMovesTheStoneOneTexel(int skinWidth, int skinHeight) {
            float skinU = 13.5f / skinWidth;
            float skinV = 7.5f / skinHeight;
            float[] here = PetrifyStoneModel.stoneCoord(skinU, skinV, skinWidth, skinHeight, STONE_SIZE);
            float[] acrossOne = PetrifyStoneModel.stoneCoord(skinU + 1f / skinWidth, skinV, skinWidth, skinHeight,
                    STONE_SIZE);
            float[] downOne = PetrifyStoneModel.stoneCoord(skinU, skinV + 1f / skinHeight, skinWidth, skinHeight,
                    STONE_SIZE);

            assertEquals(1f, (acrossOne[0] - here[0]) * STONE_SIZE, TEXEL_AGREEMENT, "stone texels per skin texel across");
            assertEquals(0f, acrossOne[1] - here[1], TEXEL_AGREEMENT, "a step across moves the stone down");
            assertEquals(1f, (downOne[1] - here[1]) * STONE_SIZE, TEXEL_AGREEMENT, "stone texels per skin texel down");
            assertEquals(0f, downOne[0] - here[0], TEXEL_AGREEMENT, "a step down moves the stone across");
        }

        @Test
        void theShaderSamplesTheStoneAtTheModeledCoordinate() throws IOException {
            assertTrue(shader("petrify_stone.fsh").contains("vec4 stone = " + PetrifyStoneModel.STONE_SAMPLE + ";"),
                    "petrify_stone.fsh samples the stone at the expression PetrifyStoneModel mirrors");
        }

        @Test
        void theVertexShaderHandsTheSkinSizeTheLayerPacks() throws IOException {
            String vertex = shader("petrify_stone.vsh");
            String fragment = shader("petrify_stone.fsh");

            assertTrue(vertex.contains("flat out vec2 skinTexels;") && vertex.contains("skinTexels = vec2(UV1);"),
                    "the vertex shader reads the skin size off the overlay coordinates ModelSkinSize packs");
            assertTrue(fragment.contains("flat in vec2 skinTexels;"), "the fragment shader takes the skin size");
        }
    }

    @Nested
    class PatchEdgesOnTheSkinGrid {

        private static final float[] SEEDED = {37.1f, 11.7f, 53.3f};
        private static final float CELLS = 6f;
        private static final float WIDE = 64f;
        private static final float SHORT = 32f;
        private static final float FIELD_AGREEMENT = 1e-6f;

        private float cornerAt(float texelU, float texelV) {
            return MingleNoiseModel.fieldAt(texelU / WIDE * CELLS + SEEDED[0], texelV / SHORT * CELLS + SEEDED[1],
                    SEEDED[2]);
        }

        private float fieldOnTheSkin(float texelU, float texelV) {
            return MingleNoiseModel.cornerField(texelU / WIDE, texelV / SHORT, WIDE, SHORT, CELLS, SEEDED, 0f);
        }

        @Test
        void aSkinTexelsCornerTakesThatCornersValue() {
            assertEquals(cornerAt(13f, 7f), fieldOnTheSkin(13f, 7f), FIELD_AGREEMENT);
        }

        @Test
        void halfwayDownASkinTexelBlendsItsCornersAboveAndBelow() {
            assertEquals((cornerAt(13f, 7f) + cornerAt(13f, 8f)) / 2f, fieldOnTheSkin(13f, 7.5f), FIELD_AGREEMENT);
        }

        @Test
        void halfwayAcrossASkinTexelBlendsItsCornersEitherSide() {
            assertEquals((cornerAt(13f, 7f) + cornerAt(14f, 7f)) / 2f, fieldOnTheSkin(13.5f, 7f), FIELD_AGREEMENT);
        }

        @Test
        void theShaderReadsTheFieldOnTheSkinsRealGrid() throws IOException {
            String stone = shader("petrify_stone.fsh");

            assertTrue(stone.contains("float field = mingleCornerField(skinCoord, skinTexels, PATCH_CELLS, "
                    + "stoneColor.rgb * SEED_SPREAD);"), "the patch field reads the skin size the layer hands over");
        }
    }
}
