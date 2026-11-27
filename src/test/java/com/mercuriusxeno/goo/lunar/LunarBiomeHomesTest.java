package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each of lunar's landforms and landmarks has its home biome: the nest and the monolith in eldritch ground alone; the
 * ruin, the boulder fields and the crater ice pools in the shadowed basins alone; the meteorite everywhere but
 * eldritch ground; and the rilles in maria alone: every climate reading a rille cuts at, the biome source resolves to
 * maria. Read from the shipped data on the classpath.
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
    /** The climate readings sampled: maria and eldritch across their spans, a step apart, and three basins readings. */
    private static final double MARIA_FROM = -0.5;
    private static final double MARIA_TO = 1.0;
    private static final double ELDRITCH_FROM = -1.0;
    private static final double ELDRITCH_TO = 1.5;
    private static final double READING_STEP = 0.025;
    private static final double[] BASINS_READINGS = {-0.5, 0.0, 0.5};

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

    /**
     * Resolves the biome the dimension's biome source places at climate readings where a rille cuts, the way the
     * source resolves it: through {@link Climate.ParameterList#findValue}, the nearest parameter point, not the range
     * bounds. Every reading a rille cuts at resolves to maria.
     */
    @Test
    void theRillesCutInMariaAlone() throws IOException {
        Climate.ParameterList<String> biomes = biomeSource();
        int cuts = 0;
        for (double m = MARIA_FROM; m <= MARIA_TO; m += READING_STEP) {
            for (double e = ELDRITCH_FROM; e <= ELDRITCH_TO; e += READING_STEP) {
                for (double basins : BASINS_READINGS) {
                    if (RilleField.profile(0.0, m, e) == 0.0) {
                        continue;
                    }
                    cuts++;
                    String biome = biomes.findValue(Climate.target((float) e, 0.0F, (float) m, (float) basins, 0.0F,
                            0.0F));
                    assertEquals(MARIA, biome, "a rille cuts at maria " + m + ", eldritch " + e + ", basins "
                            + basins + ", where the biome source places " + biome);
                }
            }
        }
        assertTrue(cuts > 0, "no sampled reading cuts a rille");
    }

    /** The dimension's biome source, its five entries read from lunar.json into the parameter list it resolves by. */
    private static Climate.ParameterList<String> biomeSource() throws IOException {
        List<Pair<Climate.ParameterPoint, String>> entries = new ArrayList<>();
        for (JsonElement entry : resourceJson(DATA + "dimension/lunar.json").getAsJsonObject("generator")
                .getAsJsonObject("biome_source").getAsJsonArray("biomes")) {
            JsonObject parameters = entry.getAsJsonObject().getAsJsonObject("parameters");
            entries.add(Pair.of(Climate.parameters(parameter(parameters, "temperature"),
                    parameter(parameters, "humidity"), parameter(parameters, "continentalness"),
                    parameter(parameters, "erosion"), parameter(parameters, "depth"), parameter(parameters, "weirdness"),
                    parameters.get("offset").getAsFloat()), entry.getAsJsonObject().get("biome").getAsString()));
        }
        return new Climate.ParameterList<>(entries);
    }

    private static Climate.Parameter parameter(JsonObject parameters, String name) {
        JsonElement range = parameters.get(name);
        return range.isJsonArray()
                ? Climate.Parameter.span(range.getAsJsonArray().get(0).getAsFloat(),
                        range.getAsJsonArray().get(1).getAsFloat())
                : Climate.Parameter.point(range.getAsFloat());
    }
}
