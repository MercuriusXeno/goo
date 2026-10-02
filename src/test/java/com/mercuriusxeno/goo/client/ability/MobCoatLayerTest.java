package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The goo splat pipeline draws through its own shader pair: the fragment
 * shader paints the goo texture laid by the splat offset under the type's
 * tint, over the half block around the hit point alone.
 */
class MobCoatLayerTest {

    private static String shaderSource(Identifier shader, String extension) throws IOException {
        String path = "/assets/" + shader.getNamespace() + "/shaders/" + shader.getPath() + extension;
        try (InputStream in = MobCoatLayerTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String fragmentSource() throws IOException {
        return shaderSource(GooRenderTypes.GOO_MOB_COAT.getFragmentShader(), ".fsh");
    }

    private static boolean holds(String source, String regex) {
        return Pattern.compile(regex).matcher(source).find();
    }

    @Test
    void pipelineDrawsThroughTheGooMobCoatShaderPair() {
        RenderPipeline pipeline = GooRenderTypes.GOO_MOB_COAT;

        assertEquals("core/goo_mob_coat", pipeline.getVertexShader().getPath());
        assertEquals("core/goo_mob_coat", pipeline.getFragmentShader().getPath());
        PipelineShaders.assertExist(pipeline);
    }

    @Nested
    class GooTexture {

        @Test
        void sampleIsLaidByTheSplatOffsetNotTheSkinUvs() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment,
                            "tile\\s*=\\s*fract\\(\\s*faceCoordinates\\(\\s*splatOffset\\s*\\)\\s*\\*\\s*TILES_PER_BLOCK\\s*\\)"),
                    "the texture is not laid by the splat offset");
            assertTrue(holds(fragment, "goo\\s*=\\s*textureLod\\(\\s*Sampler0\\s*,\\s*spriteOrigin\\s*\\+\\s*tile"),
                    "the goo texel is not read from Sampler0 at the tiled offset");
            assertFalse(fragment.contains("texCoord0"), "the splat still reads the model's skin UVs");
        }

        @Test
        void textureTilesFourTimesABlock() throws IOException {
            Matcher tiles = Pattern.compile("const float TILES_PER_BLOCK = ([0-9.]+);").matcher(fragmentSource());

            assertTrue(tiles.find(), "TILES_PER_BLOCK is not declared");
            assertEquals(4.0, Double.parseDouble(tiles.group(1)));
        }

        @Test
        void texelIsTintedByTheTypesColor() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment, "vec4\\(\\s*gooTint\\s*\\*\\s*thickness"), "the blob's light does not carry the tint");
            assertTrue(holds(fragment, "color\\s*=\\s*goo\\s*\\*\\s*litTint"), "the goo texel does not take the lit tint");
            assertTrue(holds(fragment, "fragColor\\s*=\\s*apply_fog\\(\\s*color"),
                    "the output color does not come from the tinted goo texel");
        }
    }

    @Nested
    class RaisedBlob {

        @Test
        void blobIsLitFromItsDomedNormalNotTheFlatSkin() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment, "normal\\s*=\\s*blobNormal\\(\\s*normalize\\(skinNormal\\)\\s*,\\s*fromHit\\s*\\)"),
                    "the blob's normal is not tilted from the skin's");
            assertTrue(holds(fragment, "normalize\\(normal\\s*\\+\\s*2\\.0\\s*\\*\\s*BLOB_HEIGHT\\s*\\*\\s*reachFromHit"),
                    "the tilt is not the dome's slope");
            assertTrue(holds(fragment, "minecraft_mix_light\\(Light0_Direction,\\s*Light1_Direction,\\s*normal,"),
                    "the light does not read the blob's normal");
        }

        @Test
        void blobIsThickestAtTheHitPoint() throws IOException {
            assertTrue(holds(fragmentSource(),
                            "thickness\\s*=\\s*mix\\(THIN_EDGE_SHADE,\\s*1\\.0,\\s*1\\.0\\s*-\\s*clamp\\(fromHit\\s*\\*\\s*fromHit"),
                    "the blob does not thin from the hit point to its edge");
        }
    }

    @Nested
    class SplatReach {

        @Test
        void radiusIsAQuarterBlockForAHalfBlockSplat() throws IOException {
            Matcher radius = Pattern.compile("const float SPLAT_RADIUS = ([0-9.]+);").matcher(fragmentSource());

            assertTrue(radius.find(), "SPLAT_RADIUS is not declared");
            assertEquals(0.25, Double.parseDouble(radius.group(1)));
        }

        @Test
        void fragmentsPastTheNoiseBrokenRadiusAreDiscarded() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment, "return\\s+length\\(offset\\)\\s*/\\s*SPLAT_RADIUS;")
                            && holds(fragment, "return\\s+splatDistance\\(offset\\)\\s*\\+\\s*EDGE_NOISE"),
                    "the reach is not the distance over the radius broken by noise");
            assertTrue(holds(fragment, "if\\s*\\(\\s*splatReach\\(splatOffset\\)\\s*>=\\s*mix\\("),
                    "fragments past the splat's reach are drawn");
        }
    }

    @Nested
    class Dissolve {

        @Test
        void thresholdFallsFromWholeToBelowTheNoisesLowestPullWithTheKeep() throws IOException {
            assertTrue(holds(fragmentSource(),
                            "splatReach\\(splatOffset\\)\\s*>=\\s*mix\\(\\s*-EDGE_NOISE\\s*,\\s*1\\.0\\s*,\\s*splatKeep\\s*\\)"),
                    "the keep threshold does not run from 1 down past the noise's lowest pull");
        }

        @Test
        void keepRidesTheTintsAlpha() throws IOException {
            String vertex = shaderSource(GooRenderTypes.GOO_MOB_COAT.getVertexShader(), ".vsh");

            assertTrue(holds(vertex, "splatKeep\\s*=\\s*Color\\.a;"), "the keep does not come from the tint's alpha");
        }

        @Test
        void edgeAddsNoGlowBand() throws IOException {
            String fragment = fragmentSource();

            assertFalse(fragment.toLowerCase(java.util.Locale.ROOT).contains("glow"), "the splat paints a glow band");
            assertTrue(holds(fragment, "color\\s*=\\s*goo\\s*\\*\\s*litTint\\s*\\*\\s*ColorModulator\\s*\\*\\s*lightMapColor;"),
                    "the splat's color carries more than the lit, tinted goo");
        }

        @Test
        void tintCarriesTheReachLeftAsAlphaAndKeepsItsColor() {
            int tint = 0xFFFF7A10;

            assertEquals(0xFFFF7A10, MobCoatLayer.tintWithReach(tint, 0f));
            assertEquals(0x00FF7A10, MobCoatLayer.tintWithReach(tint, 1f));
            assertEquals(0x80FF7A10, MobCoatLayer.tintWithReach(tint, 0.5f));
        }
    }

    @Test
    void vertexShaderLiftsTheSplatOffTheSkinAlongTheNormal() throws IOException {
        String vertex = shaderSource(GooRenderTypes.GOO_MOB_COAT.getVertexShader(), ".vsh");

        assertTrue(holds(vertex, "Position\\s*\\+\\s*normalize\\(Normal\\)\\s*\\*"),
                "the splat sits on the skin and fights it for depth");
    }
}
