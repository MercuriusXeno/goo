package com.mercuriusxeno.goo.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
            ", \"density\": 1000, \"viscosity\": 1000, \"elasticity\": 0.5, \"cohesion\": \"runny\", \"adhesion\": \"tops\","
            + " \"temperature\": 300,"
            + " \"extinguishes\": false,"
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
    private static final Pattern COHESION_FIELD = Pattern.compile("\"cohesion\": [^,]+");
    private static final Pattern ADHESION_FIELD = Pattern.compile("\"adhesion\": [^,]+");
    private static final Cohesion WEIRD_COHESION = Cohesion.THICK;
    /** Jelly's cohesion, the stiff rung just below firm (operator ruling 2026-11-17). */
    private static final Cohesion JELLY_COHESION = Cohesion.STIFF;
    /** Metal's cohesion, the soft rung, so a Spikes coat keeps some thickness where it lands (operator ruling 2026-11-17). */
    private static final Cohesion METAL_COHESION = Cohesion.SOFT;
    /**
     * Leaf's cohesion, the stiff rung Jelly holds (operator ruling 2026-11-17). Yarn starts from leaf's, its
     * residual to be the animal equivalent of leaf.
     */
    private static final Cohesion LEAF_COHESION = Cohesion.STIFF;
    private static final Adhesion WEIRD_ADHESION = Adhesion.CLINGS;
    private static final Adhesion LEAF_ADHESION = Adhesion.CLINGS;
    private static final Adhesion GLOW_ADHESION = Adhesion.HANGS;
    private static final Adhesion JELLY_ADHESION = Adhesion.CLINGS;
    private static final Cohesion BLAZE_COHESION = Cohesion.THICK;
    private static final Adhesion BLAZE_ADHESION = Adhesion.HANGS;
    private static final Adhesion COMMON_ADHESION = Adhesion.TOPS;
    /** Pulse's goo holds where it is laid, firm and clinging to an underside, so its layers set its signal at will. */
    private static final Cohesion PULSE_COHESION = Cohesion.FIRM;
    private static final Adhesion PULSE_ADHESION = Adhesion.HANGS;
    /** Nether's goo pours past two layers of step, the soft rung (operator ruling 2026-11-17). */
    private static final Cohesion NETHER_COHESION = Cohesion.SOFT;
    /** Nether's goo holds a block's side where it strikes, the clings rung (operator ruling 2026-11-17). */
    private static final Adhesion NETHER_ADHESION = Adhesion.CLINGS;
    /** Astral's goo pours past one layer of step, the loose rung (decision astral-goo-hangs-and-is-loose). */
    private static final Cohesion ASTRAL_COHESION = Cohesion.LOOSE;
    /** Astral's goo holds a block's underside where it strikes, the hangs rung (decision astral-goo-hangs-and-is-loose). */
    private static final Adhesion ASTRAL_ADHESION = Adhesion.HANGS;
    /**
     * Rock's goo hangs, the adhesion ladder's top rung, and is stiff, the cohesion rung one below firm, so Mortar
     * clings to sides and keeps its shape.
     * rock-goo-has-max-adhesion-and-just-below-max-cohesion
     */
    private static final Adhesion ROCK_ADHESION = Adhesion.HANGS;
    private static final Cohesion ROCK_COHESION = Cohesion.STIFF;

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
     * decodes to no textures, and bundled blaze names its own goo and fluid art.
     */
    @Test
    void texturesDecodeWhenNamedAndDefaultWhenAbsent() throws Exception {
        GooTypeDefinition named = decode("{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS
                + WATER_LIKE_FLUID + FLIGHT + ", \"textures\": {\"goo_small\": \"pack:item/small\","
                + " \"fluid_still\": \"pack:fluid/still\"}}");
        assertEquals(Optional.of(Identifier.parse("pack:item/small")), named.textures().gooSmall());
        assertEquals(Optional.of(Identifier.parse("pack:fluid/still")), named.textures().fluidStill());
        assertEquals(Optional.empty(), named.textures().gooLarge());
        assertEquals(Optional.empty(), named.textures().fluidFlowing());

        GooTypeDefinition unnamed = decode("{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS
                + WATER_LIKE_FLUID + FLIGHT + "}");
        assertEquals(GooTypeTextures.NONE, unnamed.textures());

        GooTypeTextures blaze = decodeBundled(GooTypes.BLAZE).textures();
        assertEquals(Optional.of(Identifier.parse("goo:item/blaze_goo_tiny")), blaze.goo(GooModelSize.TINY));
        assertEquals(Optional.of(Identifier.parse("goo:fluid/blaze_fluid")), blaze.fluidStill());
    }

    /**
     * Bundled infinity names a vessel source rate of 1, and a type naming none sources nothing.
     * decision infinity-sources-whatever-shares-its-vessel
     */
    @Test
    void infinityNamesAVesselSourceRateAndOtherTypesNone() throws Exception {
        assertEquals(1, decodeBundled(GooTypes.INFINITY).vesselSourcePerMb());
        assertEquals(GooTypeDefinition.NO_VESSEL_SOURCE, decodeBundled(GooTypes.BLAZE).vesselSourcePerMb());
    }

    /**
     * A texture id that is not a valid identifier is refused.
     */
    @Test
    void refusesMalformedTextureId() {
        assertTrue(parse("{\"light_level\": 8, \"saturation_fill\": 0.5" + ALL_COLORS + WATER_LIKE_FLUID + FLIGHT
                + ", \"textures\": {\"goo_base\": \"Not An Id\"}}").isError());
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
     * their own levity, durable starts from metal's, and the rest carry 1.0.
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void bundledFlightFieldsMatchDecision(ResourceKey<GooTypeDefinition> key) throws Exception {
        GooTypeDefinition type = decodeBundled(key);
        assertEquals(expectedLevity(key), type.levity(), key::toString);
        assertEquals(COMMON_BASE_FLIGHT_TIME, type.baseFlightTime(), key::toString);
    }

    /**
     * Every bundled type names a cohesion rung: weird and blaze thick, jelly, leaf and rock stiff, pulse firm,
     * metal, nether and zombie soft, astral loose, and every other type, kinetic among them, runny; zombie
     * starts from nether's physical values.
     * cohesion-is-the-step-the-goo-holds
     * blaze-goo-is-adhesion-four-and-cohesion-five
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers
     * astral-goo-hangs-and-is-loose
     * operator ruling 2026-11-17: nether's cohesion is soft
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void bundledCohesionMatchesDecision(ResourceKey<GooTypeDefinition> key) throws Exception {
        Map<ResourceKey<GooTypeDefinition>, Cohesion> stated = Map.ofEntries(
                Map.entry(GooTypes.WEIRD, WEIRD_COHESION), Map.entry(GooTypes.BLAZE, BLAZE_COHESION),
                Map.entry(GooTypes.JELLY, JELLY_COHESION), Map.entry(GooTypes.PULSE, PULSE_COHESION),
                Map.entry(GooTypes.METAL, METAL_COHESION), Map.entry(GooTypes.NETHER, NETHER_COHESION),
                Map.entry(GooTypes.LEAF, LEAF_COHESION), Map.entry(GooTypes.YARN, LEAF_COHESION),
                Map.entry(GooTypes.ZOMBIE, NETHER_COHESION), Map.entry(GooTypes.ROCK, ROCK_COHESION),
                Map.entry(GooTypes.ASTRAL, ASTRAL_COHESION));
        assertEquals(stated.getOrDefault(key, Cohesion.RUNNY), decodeBundled(key).cohesion(), key::toString);
    }

    /**
     * A type naming a cohesion rung decodes to that rung's threshold in layers.
     * cohesion-is-the-step-the-goo-holds
     */
    @ParameterizedTest
    @CsvSource({"runny, 0", "loose, 1", "soft, 2", "thick, 4", "stiff, 8", "firm, 15"})
    void aCohesionRungDecodesToItsThreshold(String rung, int layers) {
        assertEquals(layers, decode(withCohesion("\"" + rung + "\"")).cohesion().threshold(), rung);
    }

    /**
     * A cohesion stated as a number is refused, so a type names its rung.
     */
    @Test
    void refusesCohesionAsANumber() {
        assertTrue(parse(withCohesion("4")).isError());
    }

    /**
     * A cohesion naming no rung of the six is refused.
     */
    @Test
    void refusesCohesionOffTheRungs() {
        assertTrue(parse(withCohesion("\"gooey\"")).isError());
    }

    /**
     * Every bundled type names an adhesion rung: kinetic slips, weird, jelly, nether, zombie and leaf cling,
     * glow, blaze, pulse, rock, astral and durable hang, and every other type holds tops.
     * adhesion-is-a-ladder-of-holds
     * durable-goo-is-a-protective-coating-that-blocks-damage
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers
     * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
     * vines-goo-on-walls-places-goologged-vines-and-depletes
     * blaze-goo-is-adhesion-four-and-cohesion-five
     * astral-goo-hangs-and-is-loose
     * operator ruling 2026-11-17: nether clings
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void bundledAdhesionMatchesDecision(ResourceKey<GooTypeDefinition> key) throws Exception {
        Map<ResourceKey<GooTypeDefinition>, Adhesion> stated = Map.ofEntries(
                Map.entry(GooTypes.KINETIC, Adhesion.SLIPS), Map.entry(GooTypes.WEIRD, WEIRD_ADHESION),
                Map.entry(GooTypes.GLOW, GLOW_ADHESION), Map.entry(GooTypes.BLAZE, BLAZE_ADHESION),
                Map.entry(GooTypes.JELLY, JELLY_ADHESION), Map.entry(GooTypes.PULSE, PULSE_ADHESION),
                Map.entry(GooTypes.NETHER, NETHER_ADHESION), Map.entry(GooTypes.LEAF, LEAF_ADHESION),
                Map.entry(GooTypes.YARN, LEAF_ADHESION), Map.entry(GooTypes.ZOMBIE, NETHER_ADHESION),
                Map.entry(GooTypes.ROCK, ROCK_ADHESION), Map.entry(GooTypes.ASTRAL, ASTRAL_ADHESION),
                Map.entry(GooTypes.DURABLE, Adhesion.HANGS));
        Adhesion expected = stated.getOrDefault(key, COMMON_ADHESION);
        assertEquals(expected, decodeBundled(key).adhesion(), key::toString);
    }

    /**
     * A type naming an adhesion rung decodes to that rung's level on the ladder.
     * adhesion-is-a-ladder-of-holds
     */
    @ParameterizedTest
    @CsvSource({"slips, 0", "tops, 1", "gels, 2", "clings, 3", "hangs, 4"})
    void anAdhesionRungDecodesToItsLevel(String rung, int level) {
        assertEquals(level, decode(withAdhesion("\"" + rung + "\"")).adhesion().level(), rung);
    }

    /**
     * An adhesion stated as a number is refused, so a type names its rung.
     */
    @Test
    void refusesAdhesionAsANumber() {
        assertTrue(parse(withAdhesion("3")).isError());
    }

    /**
     * An adhesion naming no rung of the five is refused.
     */
    @Test
    void refusesAdhesionOffTheRungs() {
        assertTrue(parse(withAdhesion("\"sticky\"")).isError());
    }

    private static String withAdhesion(String spelled) {
        return ADHESION_FIELD.matcher(BODY_BEFORE_FLIGHT).replaceFirst("\"adhesion\": " + spelled) + FLIGHT + "}";
    }

    private static String withCohesion(String spelled) {
        return COHESION_FIELD.matcher(BODY_BEFORE_FLIGHT).replaceFirst("\"cohesion\": " + spelled) + FLIGHT + "}";
    }

    private static float expectedLevity(ResourceKey<GooTypeDefinition> key) {
        if (key == GooTypes.GLOW) {
            return GLOW_LEVITY;
        }
        if (key == GooTypes.TYPHOON) {
            return TYPHOON_LEVITY;
        }
        if (key == GooTypes.METAL || key == GooTypes.DURABLE) {
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
