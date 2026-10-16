package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The drink's screen-space fluid shaders do what the Java states: a splat
 * writes its own shape's depth and its radius as coverage, the blur carries
 * DrinkBlur's kernel, and the shade builds its normal as DrinkBlur does and
 * writes the fluid's depth, the skin two solid colors with no texture
 * (decisions unmake-drink-draws-as-a-screen-space-fluid and
 * unmake-skin-is-a-solid-sampled-color). Read through the classpath.
 */
class DrinkShaderTest {

    private static final String SPLAT_FRAGMENT = "/assets/goo/shaders/core/drink_splat.fsh";
    private static final String SPLAT_VERTEX = "/assets/goo/shaders/core/drink_splat.vsh";
    private static final String BLUR_FRAGMENT = "/assets/goo/shaders/core/drink_blur.fsh";
    private static final String SHADE_FRAGMENT = "/assets/goo/shaders/core/drink_shade.fsh";
    private static final double DELTA = 1e-9;
    private static final double CORNER_DELTA = 1e-6;
    /** The steps an 8-bit color channel holds above zero. */
    private static final double CHANNEL_STEPS = 255;
    /** How tight the glisten's highlight is. */
    private static final double GLISTEN_POWER = 48;
    /** How bright the highlight adds. */
    private static final double GLISTEN_STRENGTH = 0.35;
    /** How close to grazing the rim brightens. */
    private static final double RIM_POWER = 3;
    /** How bright the rim adds. */
    private static final double RIM_STRENGTH = 0.25;

    private static String source(String path) throws IOException {
        try (InputStream in = DrinkShaderTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static double constant(String source, String name) {
        Matcher matcher = Pattern.compile("const (?:float|int) " + name + " = ([-0-9.e]+);").matcher(source);
        assertTrue(matcher.find(), name);
        return Double.parseDouble(matcher.group(1));
    }

    @Test
    void aSplatWritesItsShapesOwnSurfaceDepthAndDiscardsAMiss() throws IOException {
        String fragment = source(SPLAT_FRAGMENT);

        assertTrue(fragment.contains("float h = along * along - dot(center, center) + bound * bound;\n"
                + "    if (h < 0.0) {\n        discard;\n    }"), "a ray missing the sphere is discarded");
        assertTrue(fragment.contains("float t = along - sqrt(h);"), "the hit is the ray's first meeting with the sphere");
        assertTrue(fragment.contains("        if (!hit) {\n            discard;\n        }"),
                "a ray missing the block's rounded cube is discarded");
        assertTrue(fragment.contains("vec3 p = rd * t;\n")
                && fragment.contains("vec4 clip = ProjMat * ModelViewMat * vec4(p, 1.0);")
                && fragment.contains("gl_FragDepth = Frame.x > 0.5 ? ndc : ndc * 0.5 + 0.5;"),
                "the depth written is the hit's, not the quad's");
        assertEquals(Math.sqrt(3), constant(fragment, "CORNER"), CORNER_DELTA);
        assertTrue(source(SPLAT_VERTEX).contains("rayPoint = Position;"), "the quad hands the fragment its eye ray");
    }

    @Test
    void aSplatsCoverageIsItsRadiusOverTheScaleTheBlurReadsItBy() throws IOException {
        String fragment = source(SPLAT_FRAGMENT);

        assertEquals(DrinkBlur.RADIUS_SCALE, constant(fragment, "RADIUS_SCALE"), DELTA);
        assertTrue(Math.round(constant(fragment, "LEAST_COVERAGE") * CHANNEL_STEPS) / CHANNEL_STEPS > DrinkBlur.COVERED,
                "the thinnest splat still reads as covered after the 8-bit coverage channel rounds it");
        assertTrue(fragment.contains("float coverage = max(halfWidth / RADIUS_SCALE, LEAST_COVERAGE);"));
    }

    @Test
    void theBlurCarriesDrinkBlursKernel() throws IOException {
        String fragment = source(BLUR_FRAGMENT);

        assertEquals(DrinkBlur.MOST_REACH, constant(fragment, "MOST_REACH"), DELTA);
        assertEquals(DrinkBlur.WIDTH_SHARE, constant(fragment, "WIDTH_SHARE"), DELTA);
        assertEquals(DrinkBlur.DEPTH_SHARE, constant(fragment, "DEPTH_SHARE"), DELTA);
        assertEquals(DrinkBlur.RADIUS_SCALE, constant(fragment, "RADIUS_SCALE"), DELTA);
        assertEquals(DrinkBlur.COVERED, constant(fragment, "COVERED"), DELTA);
        assertTrue(fragment.contains("float inside = 1.0 - share * share;\n    return inside <= 0.0 ? 0.0 : inside * inside;")
                && fragment.contains("return falloff(offset) * falloff(depthDelta / threshold);"),
                "the weight is DrinkBlur.weightOf's, falling to nothing at its edges");
        assertTrue(fragment.contains("float width = clamp(WIDTH_SHARE * radius * pixelsPerBlock, 1.0, float(MOST_REACH));"),
                "the disc is as wide on screen as the sphere up to its cap, as DrinkBlur.widthOf");
        assertTrue(fragment.contains("for (int down = -reach; down <= reach; down++) {")
                && fragment.contains("for (int across = -reach; across <= reach; across++) {")
                && fragment.contains("float share = length(vec2(across, down)) / width;")
                && fragment.contains("float weight = weightOf(share, there - here, threshold);"),
                "it reads every pixel of the disc, both axes at once, as DrinkBlur.blurImage");
        assertTrue(fragment.contains("fragColor = vec4(colors / weights, center.a);"),
                "a pixel keeps its own sphere's radius as its coverage");
        assertTrue(fragment.contains("if (center.a < COVERED) {\n        fragColor = center;\n        gl_FragDepth = depth;"),
                "a pixel no splat covered keeps the world's depth");
        assertTrue(fragment.contains("gl_FragDepth = depthOf(distances / weights);"), "the blurred depth is written");
    }

    @Test
    void theShadeBuildsItsNormalFromTheSmallerStepAndWritesTheFluidsDepth() throws IOException {
        String fragment = source(SHADE_FRAGMENT);

        assertEquals(DrinkBlur.COVERED, constant(fragment, "COVERED"), DELTA);
        assertTrue(fragment.contains("return abs(forward) < abs(backward) ? after - at : at - before;"),
                "each axis takes the smaller step, as DrinkBlur.slopeAt");
        assertTrue(fragment.contains("vec3 n = cross(across, up);"), "the normal crosses the two axes' steps");
        assertTrue(fragment.contains("float depth = texelFetch(FluidDepth, pixel, 0).r;")
                && fragment.contains("gl_FragDepth = depth;"), "the blurred depth reaches the main target");
        assertTrue(fragment.contains("if (skin.a < COVERED) {"), "a pixel no splat covered is left alone");
    }

    /** decision unmake-metaball-glistens-and-shades */
    @Test
    void theSkinGlistensFromTheNormalAndTheViewBeforeTheLightMix() throws IOException {
        String fragment = source(SHADE_FRAGMENT);

        assertEquals(GLISTEN_POWER, constant(fragment, "GLISTEN_POWER"), DELTA);
        assertEquals(GLISTEN_STRENGTH, constant(fragment, "GLISTEN_STRENGTH"), DELTA);
        assertEquals(RIM_POWER, constant(fragment, "RIM_POWER"), DELTA);
        assertEquals(RIM_STRENGTH, constant(fragment, "RIM_STRENGTH"), DELTA);
        assertTrue(fragment.contains("pow(max(dot(n, normalize(Light0_Direction + view)), 0.0), GLISTEN_POWER)")
                && fragment.contains("pow(max(dot(n, normalize(Light1_Direction + view)), 0.0), GLISTEN_POWER)"),
                "a specular highlight from each cardinal light's half vector");
        assertTrue(fragment.contains("float rim = pow(1.0 - max(dot(n, view), 0.0), RIM_POWER);"),
                "a rim brightening at grazing angles");
        int glistened = fragment.indexOf("vec4 color = glisten(vec4(skin.rgb, 1.0), viewNormal, toEye);");
        int mixed = fragment.indexOf("color = minecraft_mix_light(Light0_Direction, Light1_Direction, viewNormal, color);");
        assertTrue(glistened >= 0 && mixed > glistened, "the skin glistens before the light mix");
    }

    /** decision unmake-skin-is-a-solid-sampled-color */
    @Test
    void theSkinSamplesNoTexture() throws IOException {
        for (String path : List.of(SPLAT_FRAGMENT, BLUR_FRAGMENT, SHADE_FRAGMENT)) {
            assertFalse(source(path).contains("Sampler0"), path + " samples no texture");
        }
    }

    @Test
    void theSplatBlurAndShadePipelinesWriteDepthAndRegister() {
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);

        GooRenderTypes.registerPipelines(event);

        for (RenderPipeline pipeline : List.of(GooRenderTypes.DRINK_SPLAT, GooRenderTypes.DRINK_BLUR,
                GooRenderTypes.DRINK_SHADE)) {
            DepthStencilState depth = pipeline.getDepthStencilState();
            assertNotNull(depth);
            assertTrue(depth.writeDepth(), pipeline.getLocation() + " writes depth");
            verify(event).registerPipeline(pipeline);
        }
        assertEquals(Identifier.fromNamespaceAndPath("goo", "core/drink_splat"),
                GooRenderTypes.DRINK_SPLAT.getFragmentShader());
        assertEquals(Identifier.fromNamespaceAndPath("goo", "core/drink_blur"),
                GooRenderTypes.DRINK_BLUR.getFragmentShader());
        assertEquals(Identifier.fromNamespaceAndPath("goo", "core/drink_shade"),
                GooRenderTypes.DRINK_SHADE.getFragmentShader());
    }
}
