package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's dimension names its four surface biomes, maria, highlands, shadowed basins and eldritch ground, in its
 * biome source; read from the shipped dimension JSON on the classpath.
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 */
class LunarBiomesTest {

    private static final String DIMENSION = "/data/goo/dimension/lunar.json";
    private static final List<String> SURFACE_BIOMES = List.of("goo:lunar_maria", "goo:lunar_highlands",
            "goo:lunar_shadowed_basins", "goo:lunar_eldritch_ground");

    @Test
    void theDimensionNamesTheFourSurfaceBiomes() throws IOException {
        Set<String> named = new HashSet<>();
        for (JsonElement entry : dimension().getAsJsonObject("generator").getAsJsonObject("biome_source")
                .getAsJsonArray("biomes")) {
            named.add(entry.getAsJsonObject().get("biome").getAsString());
        }
        assertTrue(named.containsAll(SURFACE_BIOMES), DIMENSION + " should name " + SURFACE_BIOMES + ", names "
                + named);
    }

    private static JsonObject dimension() throws IOException {
        try (InputStream stream = LunarBiomesTest.class.getResourceAsStream(DIMENSION)) {
            assertNotNull(stream, DIMENSION + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
