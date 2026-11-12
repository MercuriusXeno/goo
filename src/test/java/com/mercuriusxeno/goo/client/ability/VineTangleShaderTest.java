package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.MingleNoiseModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.spider.SpiderModel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entangle's vines read each mob's own skin size: the layer hands it to the
 * shader, the patch edges lie on the skin's texel grid, and one skin texel
 * spans one vine texel along either axis
 * (decision diagnose-and-correct-vines-and-ailment-overlay-assuming-a-square-skin).
 * Shaders are read through the classpath.
 */
class VineTangleShaderTest {

    private static final String SHADERS = "/assets/goo/shaders/core/";
    /** Vanilla's vine texture, 16 texels on a side. */
    private static final int VINE_SIZE = 16;
    private static final float TEXEL_AGREEMENT = 1e-4f;

    private static String shader(String name) throws IOException {
        try (InputStream in = VineTangleShaderTest.class.getResourceAsStream(SHADERS + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static void assertUnpacksTo(int width, int height, int overlayCoords) {
        assertEquals(width, overlayCoords & 0xFFFF, "the width rides the overlay's u, as UV1.x reads it");
        assertEquals(height, overlayCoords >>> 16, "the height rides the overlay's v, as UV1.y reads it");
    }

    @Nested
    class SkinSizeHandedToTheShader {

        @Test
        void aSpiderHandsItsSixtyFourByThirtyTwoSkin() {
            assertUnpacksTo(64, 32, VineTangleLayer.skinSizeCoords(SpiderModel.createSpiderBodyLayer().bakeRoot()));
        }

        @Test
        void aZombieHandsItsSixtyFourSquareSkin() {
            assertUnpacksTo(64, 64, VineTangleLayer.skinSizeCoords(LayerDefinition.create(
                    HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64).bakeRoot()));
        }

        @Test
        void theVertexShaderPassesTheSkinSizeToTheFragmentShader() throws IOException {
            String vertex = shader("vine_tangle.vsh");
            String fragment = shader("vine_tangle.fsh");

            assertTrue(vertex.contains("flat out vec2 skinTexels;") && vertex.contains("skinTexels = vec2(UV1);"),
                    "the vertex shader reads the skin size off the overlay coordinates the layer packs");
            assertTrue(fragment.contains("flat in vec2 skinTexels;"), "the fragment shader takes the skin size");
        }
    }

    @Nested
    class PatchEdgesOnTheSkinGrid {

        private static final float[] NO_OFFSET = {0f, 0f, 0f};
        /** vine_tangle.fsh's PATCH_CELLS. */
        private static final float CELLS = 6f;
        private static final float WIDE = 64f;
        private static final float SHORT = 32f;
        private static final float FIELD_AGREEMENT = 1e-6f;

        private float cornerAt(float texelU, float texelV) {
            return MingleNoiseModel.fieldAt(texelU / WIDE * CELLS, texelV / SHORT * CELLS, 0f);
        }

        private float fieldOnTheSkin(float texelU, float texelV) {
            return MingleNoiseModel.cornerField(texelU / WIDE, texelV / SHORT, WIDE, SHORT, CELLS, NO_OFFSET, 0f);
        }

        @Test
        void aSkinTexelsCornerTakesThatCornersValue() {
            assertEquals(cornerAt(21f, 9f), fieldOnTheSkin(21f, 9f), FIELD_AGREEMENT);
        }

        @Test
        void halfwayDownASkinTexelBlendsItsCornersAboveAndBelow() {
            assertEquals((cornerAt(21f, 9f) + cornerAt(21f, 10f)) / 2f, fieldOnTheSkin(21f, 9.5f), FIELD_AGREEMENT);
        }

        @Test
        void halfwayAcrossASkinTexelBlendsItsCornersEitherSide() {
            assertEquals((cornerAt(21f, 9f) + cornerAt(22f, 9f)) / 2f, fieldOnTheSkin(21.5f, 9f), FIELD_AGREEMENT);
        }

        @Test
        void theShaderReadsTheFieldOnTheSkinsRealGrid() throws IOException {
            assertTrue(shader("vine_tangle.fsh").contains(
                    "mingleCornerField(skinCoord, skinTexels, PATCH_CELLS, vec3(0.0)) < mingleThreshold(share)"),
                    "the patch field reads the skin size the layer hands over");
        }
    }

    @Nested
    class VineTexelPerSkinTexel {

        @ParameterizedTest(name = "a {0} by {1} skin")
        @CsvSource({"64, 64", "64, 32", "128, 64"})
        void oneSkinTexelAlongEitherAxisMovesTheVineOneTexel(int skinWidth, int skinHeight) {
            float skinU = 13.5f / skinWidth;
            float skinV = 7.5f / skinHeight;
            float[] here = VineTangleModel.vineCoord(skinU, skinV, skinWidth, skinHeight, VINE_SIZE);
            float[] acrossOne = VineTangleModel.vineCoord(skinU + 1f / skinWidth, skinV, skinWidth, skinHeight,
                    VINE_SIZE);
            float[] downOne = VineTangleModel.vineCoord(skinU, skinV + 1f / skinHeight, skinWidth, skinHeight,
                    VINE_SIZE);

            assertEquals(1f, (acrossOne[0] - here[0]) * VINE_SIZE, TEXEL_AGREEMENT, "vine texels per skin texel across");
            assertEquals(0f, acrossOne[1] - here[1], TEXEL_AGREEMENT, "a step across moves the vine down");
            assertEquals(1f, (downOne[1] - here[1]) * VINE_SIZE, TEXEL_AGREEMENT, "vine texels per skin texel down");
            assertEquals(0f, downOne[0] - here[0], TEXEL_AGREEMENT, "a step down moves the vine across");
        }

        @Test
        void theShaderSamplesTheVineAtTheModeledCoordinate() throws IOException {
            assertTrue(shader("vine_tangle.fsh").contains("vec4 vine = " + VineTangleModel.VINE_SAMPLE + ";"),
                    "vine_tangle.fsh samples the vine at the expression VineTangleModel mirrors");
        }
    }
}
