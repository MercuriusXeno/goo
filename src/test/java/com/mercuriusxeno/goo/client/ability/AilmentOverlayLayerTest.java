package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.AilmentPattern;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ailment overlay draws through its own shader pair, one shader whose
 * field each ailment's pattern picks and whose color each ailment's color
 * sets (decision ailment-overlay-shader-per-ailment).
 */
class AilmentOverlayLayerTest {

    private static final int RGB_MASK = 0xFFFFFF;
    /** Stands in for the client's goo type registry: every type reads this one color. */
    private static final ToIntFunction<ResourceKey<GooTypeDefinition>> TYPE_COLOR = key -> 0x123456;

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

        private static Map<String, Integer> patternNumbers(String source) {
            Matcher number = Pattern.compile("const int PATTERN_(\\w+) = (\\d+);").matcher(source);
            Map<String, Integer> numbers = new HashMap<>();
            while (number.find()) {
                numbers.put(number.group(1), Integer.parseInt(number.group(2)));
            }
            return numbers;
        }

        private static Map<String, Integer> patternOrdinals() {
            return Arrays.stream(AilmentPattern.values())
                    .collect(Collectors.toMap(AilmentPattern::name, AilmentPattern::ordinal));
        }

        @Test
        void fragmentShaderNumbersExactlyTheAilmentPatternsByOrdinal() throws IOException {
            assertEquals(patternOrdinals(), patternNumbers(fragmentSource()));
        }

        @Test
        void vertexShaderNumbersEachPatternItNamesByItsOrdinal() throws IOException {
            Map<String, Integer> ordinals = patternOrdinals();
            Map<String, Integer> numbers = patternNumbers(vertexSource());

            assertFalse(numbers.isEmpty(), "the vertex shader names no pattern");
            numbers.forEach((name, number) -> assertEquals(ordinals.get(name), number, "PATTERN_" + name));
        }

        @Test
        void patternRidesTheOverlayCoordinatesU() throws IOException {
            assertTrue(holds(vertexSource(), "pattern\\s*=\\s*UV1\\.x;"), "the pattern is not read from UV1's U");
        }

        @Test
        void layerPacksTheAilmentsPatternIntoU() {
            for (AilmentKind kind : AilmentKind.values()) {
                if (kind.drawsOverlay()) {
                    assertEquals(OverlayTexture.pack(kind.pattern().ordinal(), 0),
                            AilmentOverlayLayer.patternCoords(kind), kind.name());
                }
            }
        }

        @Test
        void everyPatternDrawsItsOwnField() throws IOException {
            String fragment = fragmentSource();

            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_STONE"), "stone draws no field of its own");
            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_FROST"), "frost draws no field of its own");
            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_FACETS\\)\\s*\\{\\s*shine\\s*=\\s*facetField"),
                    "facets draw no field of their own");
            assertTrue(holds(fragment, "else\\s*if\\s*\\(pattern\\s*==\\s*PATTERN_SHIMMER\\)\\s*\\{\\s*shine\\s*=\\s*shimmerField"),
                    "the shimmer draws no field of its own");
            assertTrue(holds(fragment, "pattern\\s*==\\s*PATTERN_WOBBLE"), "the wobble draws no field of its own");
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
            int rgb = kind.rgb(TYPE_COLOR);
            assertEquals(Math.round(kind.opacity() * 0xFF), AilmentOverlayLayer.overlayColor(kind, 1f, TYPE_COLOR) >>> 24);
            assertEquals(rgb, AilmentOverlayLayer.overlayColor(kind, 0f, TYPE_COLOR));
            assertEquals(Math.round(kind.opacity() * 0x80), AilmentOverlayLayer.overlayColor(kind, 0.5f, TYPE_COLOR) >>> 24);
            assertEquals(rgb, AilmentOverlayLayer.overlayColor(kind, 0.5f, TYPE_COLOR) & RGB_MASK);
        }
    }

    /**
     * Stasis, Charm, Sunbeam and Lux, and Scry outline their mob in the
     * ailment's own color, its brightness pulsing (decision ailment-glisten-becomes-a-pulsing-outline).
     */
    @Nested
    class PulsingOutlineColor {

        private static final int ASTRAL_COLOR = 0x2A7FD3;
        private static final int RECOLORED_ASTRAL = 0x55AA11;
        private static final float HALF_PULSE = PulsingOutline.PULSE_PERIOD / 2f;

        private static int outlineAt(AilmentKind kind, float gameTime,
                                     ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
            return AilmentOverlayLayer.outlineOf(List.of(new AilmentOverlayLayer.StampedAilment(kind, 1f)),
                    gameTime, typeColor).orElseThrow();
        }

        private static ToIntFunction<ResourceKey<GooTypeDefinition>> astralReads(int rgb) {
            return key -> key.equals(GooTypes.ASTRAL) ? rgb : 0;
        }

        @ParameterizedTest
        @CsvSource({"STASIS, 0xFFD447", "HEX, 0x6A1FB0", "GLOW, 0xFFE628"})
        void outlineDrawsInTheAilmentsOwnColor(AilmentKind kind, int rgb) {
            assertEquals(ARGB.opaque(rgb), outlineAt(kind, 0f, TYPE_COLOR));
        }

        @Test
        void astralOutlineFollowsTheAstralTypesColor() {
            assertEquals(ARGB.opaque(ASTRAL_COLOR), outlineAt(AilmentKind.ASTRAL, 0f, astralReads(ASTRAL_COLOR)));
            assertEquals(ARGB.opaque(RECOLORED_ASTRAL),
                    outlineAt(AilmentKind.ASTRAL, 0f, astralReads(RECOLORED_ASTRAL)));
        }

        @ParameterizedTest
        @EnumSource(value = AilmentKind.class, names = {"STASIS", "HEX", "GLOW", "ASTRAL"})
        void outlineBrightnessPulses(AilmentKind kind) {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = astralReads(ASTRAL_COLOR);
            assertNotEquals(outlineAt(kind, 0f, typeColor), outlineAt(kind, HALF_PULSE, typeColor));
        }
    }

    /** A charmed mob wears the hex outline whole for as long as its charm holds (decision charm-holds-until-struck). */
    @Nested
    class CharmOutline {

        private static final AilmentOverlayLayer.StampedAilment FADING_HEX =
                new AilmentOverlayLayer.StampedAilment(AilmentKind.HEX, 0.2f);
        private static final AilmentOverlayLayer.StampedAilment SCALES =
                new AilmentOverlayLayer.StampedAilment(AilmentKind.SCALES, 1f);
        private static final AilmentOverlayLayer.StampedAilment WHOLE_HEX =
                new AilmentOverlayLayer.StampedAilment(AilmentKind.HEX, 1f);

        @Test
        void charmedMobWearingNothingWearsTheWholeHexOutline() {
            List<AilmentOverlayLayer.StampedAilment> stamped = AilmentOverlayLayer.withCharmOutline(List.of(), true);

            assertEquals(List.of(WHOLE_HEX), stamped);
            assertEquals(OptionalInt.of(ARGB.opaque(AilmentKind.HEX.rgb())),
                    AilmentOverlayLayer.outlineOf(stamped, 0f, TYPE_COLOR));
        }

        @Test
        void charmedMobWearingAnotherAilmentWearsTheWholeHexOutlineBesideIt() {
            List<AilmentOverlayLayer.StampedAilment> stamped =
                    AilmentOverlayLayer.withCharmOutline(List.of(SCALES), true);

            assertEquals(List.of(SCALES, WHOLE_HEX), stamped);
            assertEquals(OptionalInt.of(ARGB.opaque(AilmentKind.HEX.rgb())),
                    AilmentOverlayLayer.outlineOf(stamped, 0f, TYPE_COLOR));
        }

        @Test
        void charmedMobWearsTheWholeHexOverAFadingOne() {
            assertEquals(List.of(SCALES, WHOLE_HEX),
                    AilmentOverlayLayer.withCharmOutline(List.of(FADING_HEX, SCALES), true));
        }

        @Test
        void uncharmedMobWearsOnlyItsTimedAilments() {
            assertEquals(List.of(FADING_HEX, SCALES),
                    AilmentOverlayLayer.withCharmOutline(List.of(FADING_HEX, SCALES), false));
        }

        @Test
        void charmedMobWearsTheHeartOverItsHead() {
            assertEquals(Optional.of(HeadMark.CHARMED_HEART), HeadMark.of(true, false));
        }
    }

    @Test
    void vertexShaderEncasesTheModelAlongItsNormals() throws IOException {
        assertTrue(holds(vertexSource(), "Position\\s*\\+\\s*normalize\\(Normal\\)\\s*\\*\\s*OVERLAY_INFLATE"),
                "the overlay sits on the skin and fights it for depth");
    }

    /** A held mob flails with no overlay over it (decision kinetic-is-electric-blue-and-white). */
    @Test
    void aGrabbedMobWearsNoOverlayBesideItsOtherAilments() {
        List<MobAilments.Worn> worn = List.of(new MobAilments.Worn(AilmentKind.HEX, 40),
                new MobAilments.Worn(AilmentKind.GRABBED, 4));
        assertEquals(List.of(AilmentKind.HEX),
                AilmentOverlayLayer.drawn(worn, 0f).stream().map(AilmentOverlayLayer.StampedAilment::kind).toList());
    }
}
