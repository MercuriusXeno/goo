package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.AilmentPattern;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ailment overlay draws through its own shader pair, one shader whose
 * field each ailment's pattern picks and whose color each ailment's color
 * sets (decision ailment-overlay-shader-per-ailment).
 */
class AilmentOverlayLayerTest {

    private static final int RGB_MASK = 0xFFFFFF;

    private static String shaderSource(Identifier shader, String extension) throws IOException {
        String path = "/assets/" + shader.getNamespace() + "/shaders/" + shader.getPath() + extension;
        try (InputStream in = AilmentOverlayLayerTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String fragmentSource() throws IOException {
        return shaderSource(GooRenderTypes.GOO_AILMENT_OVERLAY.getFragmentShader(), ".fsh");
    }

    private static String vertexSource() throws IOException {
        return shaderSource(GooRenderTypes.GOO_AILMENT_OVERLAY.getVertexShader(), ".vsh");
    }

    private static boolean holds(String source, String regex) {
        return Pattern.compile(regex).matcher(source).find();
    }

    @Test
    void pipelineDrawsThroughTheAilmentOverlayShaderPair() {
        RenderPipeline pipeline = GooRenderTypes.GOO_AILMENT_OVERLAY;

        assertEquals("core/goo_ailment_overlay", pipeline.getVertexShader().getPath());
        assertEquals("core/goo_ailment_overlay", pipeline.getFragmentShader().getPath());
        PipelineShaders.assertExist(pipeline);
    }

    @Nested
    class PatternPick {

        @ParameterizedTest
        @EnumSource(AilmentPattern.class)
        void shaderNumbersEachPatternByItsOrdinal(AilmentPattern pattern) throws IOException {
            Matcher number = Pattern.compile("const int PATTERN_" + pattern.name() + " = (\\d+);")
                    .matcher(fragmentSource());

            assertTrue(number.find(), "PATTERN_" + pattern.name() + " is not declared");
            assertEquals(pattern.ordinal(), Integer.parseInt(number.group(1)));
        }

        @Test
        void patternRidesTheOverlayCoordinatesU() throws IOException {
            assertTrue(holds(vertexSource(), "pattern\\s*=\\s*UV1\\.x;"), "the pattern is not read from UV1's U");
        }

        @ParameterizedTest
        @EnumSource(AilmentKind.class)
        void layerPacksTheAilmentsPatternIntoU(AilmentKind kind) {
            assertEquals(OverlayTexture.pack(kind.pattern().ordinal(), 0), AilmentOverlayLayer.patternCoords(kind));
        }

        @Test
        void everyPatternDrawsItsOwnField() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_STONE"), "stone draws no field of its own");
            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_FROST"), "frost draws no field of its own");
            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_FACETS\\)\\s*\\{\\s*shine\\s*=\\s*facetField"),
                    "facets draw no field of their own");
            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_SHIMMER\\)\\s*\\{\\s*shine\\s*=\\s*shimmerField"),
                    "the shimmer draws no field of its own");
            assertTrue(holds(fragment, "else\\s*\\{\\s*shine\\s*=\\s*glintField"), "the glint is not the default field");
        }
    }

    @Nested
    class Color {

        @Test
        void overlayIsTintedByTheAilmentsColorWithItsStrengthAsAlpha() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment, "strength\\s*=\\s*ailmentColor\\.a;"), "the strength does not ride the alpha");
            assertTrue(holds(fragment, "tint\\s*=\\s*ailmentColor\\.rgb;"), "the tint is not the ailment's color");
            assertTrue(holds(vertexSource(), "ailmentColor\\s*=\\s*Color;"), "the vertex color is not handed on");
        }

        @ParameterizedTest
        @EnumSource(AilmentKind.class)
        void layerColorIsTheAilmentsRgbUnderItsStrength(AilmentKind kind) {
            assertEquals(Math.round(kind.opacity() * 0xFF), AilmentOverlayLayer.overlayColor(kind, 1f) >>> 24);
            assertEquals(kind.rgb(), AilmentOverlayLayer.overlayColor(kind, 0f));
            assertEquals(Math.round(kind.opacity() * 0x80), AilmentOverlayLayer.overlayColor(kind, 0.5f) >>> 24);
            assertEquals(kind.rgb(), AilmentOverlayLayer.overlayColor(kind, 0.5f) & RGB_MASK);
        }

        /** Hex's glisten draws lighter than the stasis shimmer at the same strength. */
        @Test
        void hexGlistenDrawsLighterThanFull() {
            assertTrue(AilmentOverlayLayer.overlayColor(AilmentKind.HEX, 1f) >>> 24
                    < AilmentOverlayLayer.overlayColor(AilmentKind.STASIS, 1f) >>> 24);
        }

        /** Scry's glisten draws lighter still, since it marks every mob in its sphere at once. */
        @Test
        void glowGlistenDrawsLighterThanHex() {
            assertTrue(AilmentOverlayLayer.overlayColor(AilmentKind.GLOW, 1f) >>> 24
                    < AilmentOverlayLayer.overlayColor(AilmentKind.HEX, 1f) >>> 24);
        }
    }

    @Test
    void glintPatternsScrollTheVanillaGlintOverTime() throws IOException {
        String fragment = fragmentSource();

        assertTrue(holds(fragment, "textureLod\\(Sampler0,"), "the glint texture is not read");
        assertTrue(holds(fragment, "glintField\\(skinCoord,\\s*GameTime\\)"), "the glint does not move with time");
    }

    @Test
    void vertexShaderEncasesTheModelAlongItsNormals() throws IOException {
        assertTrue(holds(vertexSource(), "Position\\s*\\+\\s*normalize\\(Normal\\)\\s*\\*\\s*OVERLAY_INFLATE"),
                "the overlay sits on the skin and fights it for depth");
    }
}
