package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each of lunar's four surface biomes names the air geyser feature among its features, read from the shipped biome
 * JSON on the classpath.
 * decision air-geysers-of-updraft-goo-dot-lunar
 */
class LunarAirGeysersTest {

    private static final String AIR_GEYSERS = "goo:air_geysers";

    @ParameterizedTest
    @ValueSource(strings = {"lunar_maria", "lunar_highlands", "lunar_shadowed_basins", "lunar_eldritch_ground"})
    void eachSurfaceBiomeNamesTheAirGeysers(String biome) throws IOException {
        List<String> named = featuresOf("/data/goo/worldgen/biome/" + biome + ".json");
        assertTrue(named.contains(AIR_GEYSERS), biome + " should name " + AIR_GEYSERS + ", names " + named);
    }

    private static List<String> featuresOf(String path) throws IOException {
        try (InputStream stream = LunarAirGeysersTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            JsonObject biome = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            List<String> named = new ArrayList<>();
            for (JsonElement step : biome.getAsJsonArray("features")) {
                for (JsonElement feature : (JsonArray) step) {
                    named.add(feature.getAsString());
                }
            }
            return named;
        }
    }
}
