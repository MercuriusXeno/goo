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

            assertTrue(holds(fragment, "tile\\s*=\\s*fract\\(\\s*faceCoordinates\\(\\s*splatOffset\\s*\\)\\s*\\)"),
                    "the texture is not laid by the splat offset");
            assertTrue(holds(fragment, "goo\\s*=\\s*textureLod\\(\\s*Sampler0\\s*,\\s*spriteOrigin\\s*\\+\\s*tile"),
                    "the goo texel is not read from Sampler0 at the tiled offset");
            assertFalse(fragment.contains("texCoord0"), "the splat still reads the model's skin UVs");
        }

        @Test
        void texelIsTintedByTheVertexColor() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment, "color\\s*=\\s*goo\\s*\\*\\s*vertexColor"),
                    "the goo texel does not take the type's tint");
            assertTrue(holds(fragment, "fragColor\\s*=\\s*apply_fog\\(\\s*color"),
                    "the output color does not come from the tinted goo texel");
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

            assertTrue(holds(fragment, "length\\(offset\\)\\s*/\\s*SPLAT_RADIUS\\s*\\+\\s*EDGE_NOISE"),
                    "the reach is not the distance over the radius broken by noise");
            assertTrue(holds(fragment, "if\\s*\\(\\s*splatReach\\(splatOffset\\)\\s*>=\\s*1\\.0\\s*\\)\\s*\\{\\s*discard;"),
                    "fragments past the splat's reach are drawn");
        }
    }

    @Test
    void vertexShaderLiftsTheSplatOffTheSkinAlongTheNormal() throws IOException {
        String vertex = shaderSource(GooRenderTypes.GOO_MOB_COAT.getVertexShader(), ".vsh");

        assertTrue(holds(vertex, "Position\\s*\\+\\s*normalize\\(Normal\\)\\s*\\*"),
                "the splat sits on the skin and fights it for depth");
    }
}
