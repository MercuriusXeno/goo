package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each of lunar's landforms and landmarks has its home biome: the nest and the monolith in eldritch ground alone; the
 * ruin, the boulder fields and the crater ice pools in the shadowed basins alone; the meteorite everywhere but
 * eldritch ground; and the rilles in maria alone, since the rille cuts only where the maria noise reads above zero and
 * the dimension places maria, and maria alone, there. Read from the shipped data on the classpath.
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 * decision meteorites-fall-on-every-lunar-biome-but-eldritch-ground
 */
class LunarBiomeHomesTest {

    private static final String DATA = "/data/goo/";
    private static final String MARIA = "goo:lunar_maria";
    private static final String HIGHLANDS = "goo:lunar_highlands";
    private static final String BASINS = "goo:lunar_shadowed_basins";
    private static final String ELDRITCH = "goo:lunar_eldritch_ground";
    private static final List<String> LUNAR_BIOMES = List.of(MARIA, HIGHLANDS, BASINS, ELDRITCH, "goo:lunar_warrens");
    private static final double TOLERANCE = 1e-9;
    /** A maria reading on ground outside the maria, where a rille's line would otherwise cut its full depth. */
    private static final double OUTSIDE_THE_MARIA = -0.01;

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = LunarBiomeHomesTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String file(String id, String folder) {
        return DATA + folder + "/" + id.substring(id.indexOf(':') + 1) + ".json";
    }

    /** Every lunar biome whose feature lists name a placed feature. */
    private static Set<String> biomesPlacing(String feature) throws IOException {
        Set<String> homes = new HashSet<>();
        for (String biome : LUNAR_BIOMES) {
            for (JsonElement step : resourceJson(file(biome, "worldgen/biome")).getAsJsonArray("features")) {
                for (JsonElement placed : step.getAsJsonArray()) {
                    if (placed.getAsString().equals(feature)) {
                        homes.add(biome);
                    }
                }
            }
        }
        return homes;
    }

    /** The biomes a structure's biome tag names. */
    private static Set<String> structureBiomes(String structure) throws IOException {
        String tag = resourceJson(file(structure, "worldgen/structure")).get("biomes").getAsString();
        assertTrue(tag.startsWith("#goo:"), structure + " should name its biomes by a goo tag, names " + tag);
        Set<String> biomes = new HashSet<>();
        for (JsonElement biome : resourceJson(DATA + "tags/worldgen/biome/" + tag.substring("#goo:".length())
                + ".json").getAsJsonArray("values")) {
            biomes.add(biome.getAsString());
        }
        return biomes;
    }

    @ParameterizedTest
    @CsvSource({"goo:boulder_field", "goo:crater_pools"})
    void theShadowedBasinsAlonePlaceTheirFeatures(String feature) throws IOException {
        assertEquals(Set.of(BASINS), biomesPlacing(feature), feature + " should be placed in the shadowed basins alone");
    }

    @ParameterizedTest
    @CsvSource({"goo:eldritch_nest", "goo:monolith"})
    void eldritchGroundAloneHoldsTheNestAndTheMonolith(String structure) throws IOException {
        assertEquals(Set.of(ELDRITCH), structureBiomes(structure), structure + " should generate in eldritch ground "
                + "alone");
    }

    @Test
    void theShadowedBasinsAloneHoldTheRuin() throws IOException {
        assertEquals(Set.of(BASINS), structureBiomes("goo:lunar_ruin"), "the ruin should generate in the shadowed "
                + "basins alone");
    }

    @Test
    void theMeteoriteFallsEverywhereButEldritchGround() throws IOException {
        assertEquals(Set.of(MARIA, HIGHLANDS, BASINS), structureBiomes("goo:meteorite"),
                "the meteorite should generate in maria, highlands and shadowed basins");
    }

    @Test
    void theRillesCutInMariaAlone() throws IOException {
        assertEquals(0.0, RilleField.profile(0.0, OUTSIDE_THE_MARIA), TOLERANCE);
        for (JsonElement entry : resourceJson(DATA + "dimension/lunar.json").getAsJsonObject("generator")
                .getAsJsonObject("biome_source").getAsJsonArray("biomes")) {
            String biome = entry.getAsJsonObject().get("biome").getAsString();
            JsonElement continentalness = entry.getAsJsonObject().getAsJsonObject("parameters").get("continentalness");
            if (MARIA.equals(biome)) {
                assertEquals(0.0, lowerBound(continentalness), TOLERANCE,
                        "maria should hold every column the maria noise reads above zero");
            } else if (!"goo:lunar_warrens".equals(biome)) {
                assertTrue(upperBound(continentalness) <= 0.0, biome + " should hold no column the maria noise "
                        + "reads above zero, reaches " + upperBound(continentalness));
            }
        }
    }

    private static double lowerBound(JsonElement range) {
        return range.isJsonArray() ? range.getAsJsonArray().get(0).getAsDouble() : range.getAsDouble();
    }

    private static double upperBound(JsonElement range) {
        JsonArray bounds = range.isJsonArray() ? range.getAsJsonArray() : null;
        return bounds == null ? range.getAsDouble() : bounds.get(1).getAsDouble();
    }
}
