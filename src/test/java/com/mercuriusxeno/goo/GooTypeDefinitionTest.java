package com.mercuriusxeno.goo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that every bundled goo type JSON on the classpath decodes through the
 * registry codec, so the datapack registry accepts what the mod ships, that
 * the light fields decode to the values the enum used to hold, and that the
 * color channels decode from hex and read back through GooColors.
 */
class GooTypeDefinitionTest {

    private static final String BUNDLED_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final int BLAZE_PEAK = 15;
    private static final float BLAZE_SATURATION = 0.5f;
    private static final int PULSE_PEAK = 8;
    private static final float PULSE_SATURATION = 0.6f;
    private static final float UNSTABLE_SATURATION = 0.55f;
    private static final String ALL_COLORS =
            ", \"wheel\": \"112233\", \"bright\": \"445566\", \"highlight\": \"778899\", \"edge\": \"AABBCC\"";

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeys() {
        return GooTypes.BUNDLED.stream();
    }

    /**
     * Each bundled JSON decodes to a definition without error.
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void decodesBundledJson(ResourceKey<GooTypeDefinition> key) throws Exception {
        decodeBundled(key);
    }

    /**
     * The light fields carry the values the enum constructor used to name,
     * read on the types whose saturation is not the common 0.5.
     */
    @Test
    void lightFieldsMatchFormerEnumValues() throws Exception {
        assertLight(BLAZE_PEAK, BLAZE_SATURATION, decodeBundled(GooTypes.BLAZE));
        assertLight(PULSE_PEAK, PULSE_SATURATION, decodeBundled(GooTypes.PULSE));
        assertLight(BLAZE_PEAK, UNSTABLE_SATURATION, decodeBundled(GooTypes.UNSTABLE));
    }

    /**
     * The four channels decode from hex strings and read back through
     * GooColors, and the bundled blaze carries the colors the retired config held.
     */
    @Test
    void colorChannelsReadBackThroughGooColors() throws Exception {
        GooTypeDefinition custom = decode(
                "{\"light_level\": 8, \"saturation_fill\": 0.5, \"wheel\": \"112233\","
                + " \"bright\": \"445566\", \"highlight\": \"778899\", \"edge\": \"aAbBcC\"}");
        assertEquals(0x112233, GooColors.wheel(custom));
        assertEquals(0x445566, GooColors.bright(custom));
        assertEquals(0x778899, GooColors.highlight(custom));
        assertEquals(0xAABBCC, GooColors.edge(custom));
        assertEquals(GooColors.highlight(custom), GooColors.get(custom));

        GooTypeDefinition blaze = decodeBundled(GooTypes.BLAZE);
        assertEquals(0xFF6600, GooColors.wheel(blaze));
        assertEquals(0xFF8E28, GooColors.bright(blaze));
        assertEquals(0xFF750F, GooColors.highlight(blaze));
        assertEquals(0xFF841E, GooColors.edge(blaze));
    }

    /**
     * The hex codec writes six upper-case digits and refuses other spellings.
     */
    @Test
    void hexColorRoundTripsAndRefusesOtherSpellings() {
        assertEquals("FF6600", GooTypeDefinition.HEX_COLOR.encodeStart(JsonOps.INSTANCE, 0xFF6600)
                .getOrThrow().getAsString());
        assertEquals(0xFF6600, GooTypeDefinition.HEX_COLOR.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"ff6600\"")).getOrThrow());
        assertTrue(GooTypeDefinition.HEX_COLOR.parse(JsonOps.INSTANCE, JsonParser.parseString("\"#FF6600\"")).isError());
        assertTrue(GooTypeDefinition.HEX_COLOR.parse(JsonOps.INSTANCE, JsonParser.parseString("\"FF66\"")).isError());
        assertTrue(GooTypeDefinition.HEX_COLOR.parse(JsonOps.INSTANCE, JsonParser.parseString("\"GG6600\"")).isError());
    }

    /**
     * A body missing light_level is refused, so a datapack type states its light.
     */
    @Test
    void refusesBodyWithoutLightLevel() {
        assertTrue(parse("{\"saturation_fill\": 0.5" + ALL_COLORS + "}").isError());
    }

    /**
     * A light_level past the vanilla ceiling is refused.
     */
    @Test
    void refusesLightPastCeiling() {
        assertTrue(parse("{\"light_level\": 16, \"saturation_fill\": 0.5" + ALL_COLORS + "}").isError());
    }

    /**
     * A body missing a color channel is refused, so a datapack type names all four.
     */
    @Test
    void refusesBodyWithoutEdgeColor() {
        assertTrue(parse("{\"light_level\": 8, \"saturation_fill\": 0.5, \"wheel\": \"112233\","
                + " \"bright\": \"445566\", \"highlight\": \"778899\"}").isError());
    }

    private static void assertLight(int peak, float saturation, GooTypeDefinition type) {
        assertEquals(peak, type.peakLight());
        assertEquals(saturation, type.saturationFill());
    }

    private static DataResult<GooTypeDefinition> parse(String json) {
        return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static GooTypeDefinition decode(String json) {
        DataResult<GooTypeDefinition> decoded = parse(json);
        assertTrue(decoded.isSuccess(), () -> "failed to decode: " + decoded.error());
        return decoded.getOrThrow();
    }

    private static GooTypeDefinition decodeBundled(ResourceKey<GooTypeDefinition> key) throws Exception {
        String path = BUNDLED_JSON_FORMAT.formatted(key.identifier().getPath());
        try (InputStream in = GooTypeDefinitionTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Bundled JSON missing on classpath: " + path);
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            JsonElement json = JsonParser.parseReader(reader);
            DataResult<GooTypeDefinition> decoded = GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, json);
            assertTrue(decoded.isSuccess(), () -> path + " failed to decode: " + decoded.error());
            return decoded.getOrThrow();
        }
    }
}
