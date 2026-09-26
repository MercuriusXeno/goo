package com.mercuriusxeno.goo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that every bundled goo type JSON on the classpath decodes through the
 * registry codec, so the datapack registry accepts what the mod ships, that
 * the light fields decode to the values the enum used to hold, that the
 * color channels decode from hex and read back through GooColors, and that
 * the fluid fields decode to the values the fluid type switches used to hold.
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
    private static final String WATER_LIKE_FLUID =
            ", \"density\": 1000, \"viscosity\": 1000, \"temperature\": 300, \"extinguishes\": false,"
            + " \"map_color\": \"stone\"";
    private static final String FLIGHT = ", \"levity\": 1.0, \"base_flight_time\": 3";
    private static final String BODY_BEFORE_FLIGHT = "{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS
            + WATER_LIKE_FLUID;
    private static final float GLOW_LEVITY = 0.4f;
    private static final float TYPHOON_LEVITY = 1.5f;
    private static final float METAL_LEVITY = 0.7f;
    private static final float COMMON_LEVITY = 1.0f;
    private static final int COMMON_BASE_FLIGHT_TIME = 3;
    private static final int BLAZE_DENSITY = 1500;
    private static final int BLAZE_VISCOSITY = 800;
    private static final int BLAZE_TEMPERATURE = 1300;
    private static final int FROST_TEMPERATURE = 200;

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
                + " \"bright\": \"445566\", \"highlight\": \"778899\", \"edge\": \"aAbBcC\"" + WATER_LIKE_FLUID + FLIGHT + "}");
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
     * The fluid fields decode from the bundled JSON: blaze carries the
     * density, viscosity and temperature the retired fluid type switches
     * held and the fire map color, frost is cold and extinguishes.
     */
    @Test
    void fluidFieldsDecodeFromBundledJson() throws Exception {
        GooTypeDefinition blaze = decodeBundled(GooTypes.BLAZE);
        assertEquals(BLAZE_DENSITY, blaze.density());
        assertEquals(BLAZE_VISCOSITY, blaze.viscosity());
        assertEquals(BLAZE_TEMPERATURE, blaze.temperature());
        assertFalse(blaze.extinguishes());
        assertSame(MapColor.FIRE, blaze.mapColor());

        GooTypeDefinition frost = decodeBundled(GooTypes.FROST);
        assertEquals(FROST_TEMPERATURE, frost.temperature());
        assertTrue(frost.extinguishes());
        assertSame(MapColor.ICE, frost.mapColor());
    }

    /**
     * A map color is spelled by its constant name in any case, writes back
     * lower-case, and a name no constant holds is refused.
     */
    @Test
    void mapColorCodecSpellsConstantNames() {
        assertSame(MapColor.COLOR_LIGHT_GREEN, MapColors.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"Color_Light_Green\"")).getOrThrow());
        assertEquals("nether", MapColors.CODEC.encodeStart(JsonOps.INSTANCE, MapColor.NETHER)
                .getOrThrow().getAsString());
        assertTrue(MapColors.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"chartreuse\"")).isError());
    }

    /**
     * A body naming textures decodes each named id, a body naming none
     * decodes to no textures, and bundled blaze names its own blob and fluid art.
     */
    @Test
    void texturesDecodeWhenNamedAndDefaultWhenAbsent() throws Exception {
        GooTypeDefinition named = decode("{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS
                + WATER_LIKE_FLUID + FLIGHT + ", \"textures\": {\"blob_small\": \"pack:item/small\","
                + " \"fluid_still\": \"pack:fluid/still\"}}");
        assertEquals(Optional.of(Identifier.parse("pack:item/small")), named.textures().blobSmall());
        assertEquals(Optional.of(Identifier.parse("pack:fluid/still")), named.textures().fluidStill());
        assertEquals(Optional.empty(), named.textures().blobLarge());
        assertEquals(Optional.empty(), named.textures().fluidFlowing());

        GooTypeDefinition unnamed = decode("{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS
                + WATER_LIKE_FLUID + FLIGHT + "}");
        assertEquals(GooTypeTextures.NONE, unnamed.textures());

        GooTypeTextures blaze = decodeBundled(GooTypes.BLAZE).textures();
        assertEquals(Optional.of(Identifier.parse("goo:item/blaze_blob_tiny")), blaze.blob(BlobModelSize.TINY));
        assertEquals(Optional.of(Identifier.parse("goo:fluid/blaze_fluid")), blaze.fluidStill());
    }

    /**
     * A texture id that is not a valid identifier is refused.
     */
    @Test
    void refusesMalformedTextureId() {
        assertTrue(parse("{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS + WATER_LIKE_FLUID + FLIGHT
                + ", \"textures\": {\"blob_base\": \"Not An Id\"}}").isError());
    }

    /**
     * A body missing a fluid field is refused, so a datapack type states how
     * its fluid behaves.
     */
    @Test
    void refusesBodyWithoutTemperature() {
        assertTrue(parse("{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS
                + ", \"density\": 1000, \"viscosity\": 1000, \"extinguishes\": false, \"map_color\": \"stone\"" + FLIGHT + "}")
                .isError());
    }

    /**
     * A body missing light_level is refused, so a datapack type states its light.
     */
    @Test
    void refusesBodyWithoutLightLevel() {
        assertTrue(parse("{\"saturation_fill\": 0.5" + ALL_COLORS + WATER_LIKE_FLUID + FLIGHT + "}").isError());
    }

    /**
     * A light_level past the vanilla ceiling is refused.
     */
    @Test
    void refusesLightPastCeiling() {
        assertTrue(parse("{\"light_level\": 16, \"saturation_fill\": 0.5" + ALL_COLORS + WATER_LIKE_FLUID + FLIGHT + "}").isError());
    }

    /**
     * A body missing a color channel is refused, so a datapack type names all four.
     */
    @Test
    void refusesBodyWithoutEdgeColor() {
        assertTrue(parse("{\"light_level\": 8, \"saturation_fill\": 0.5, \"wheel\": \"112233\","
                + " \"bright\": \"445566\", \"highlight\": \"778899\"" + WATER_LIKE_FLUID + FLIGHT + "}").isError());
    }

    /**
     * The flight fields decode into levity and baseFlightTime, per decision
     * levity-and-base-in-goo-type-json.
     */
    @Test
    void flightFieldsDecodeIntoAccessors() {
        GooTypeDefinition type = decode(BODY_BEFORE_FLIGHT + ", \"levity\": 0.4, \"base_flight_time\": 3}");
        assertEquals(GLOW_LEVITY, type.levity());
        assertEquals(COMMON_BASE_FLIGHT_TIME, type.baseFlightTime());
    }

    /**
     * A body missing levity is refused, so a datapack type states how fast it flies.
     */
    @Test
    void refusesBodyWithoutLevity() {
        assertTrue(parse(BODY_BEFORE_FLIGHT + ", \"base_flight_time\": 3}").isError());
    }

    /**
     * A body missing base_flight_time is refused, so a datapack type states its base flight time.
     */
    @Test
    void refusesBodyWithoutBaseFlightTime() {
        assertTrue(parse(BODY_BEFORE_FLIGHT + ", \"levity\": 1.0}").isError());
    }

    /**
     * Every bundled type flies with base 3; glow, typhoon and metal carry
     * their own levity and the rest carry 1.0.
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void bundledFlightFieldsMatchDecision(ResourceKey<GooTypeDefinition> key) throws Exception {
        GooTypeDefinition type = decodeBundled(key);
        assertEquals(expectedLevity(key), type.levity(), key::toString);
        assertEquals(COMMON_BASE_FLIGHT_TIME, type.baseFlightTime(), key::toString);
    }

    private static float expectedLevity(ResourceKey<GooTypeDefinition> key) {
        if (key == GooTypes.GLOW) {
            return GLOW_LEVITY;
        }
        if (key == GooTypes.TYPHOON) {
            return TYPHOON_LEVITY;
        }
        if (key == GooTypes.METAL) {
            return METAL_LEVITY;
        }
        return COMMON_LEVITY;
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
