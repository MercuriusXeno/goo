package com.mercuriusxeno.goo.ability.outsider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The goo:pocket dimension Outsider's rooms stand in, read from its shipped JSON on the classpath: no skybox
 * above and the black goo:pocket_void biome around
 * (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform).
 */
class PocketDimensionTest {

    private static final String DIMENSION_TYPE = "/data/goo/dimension_type/pocket.json";
    private static final String DIMENSION = "/data/goo/dimension/pocket.json";

    @Test
    void thePocketDrawsNoSkybox() throws IOException {
        assertEquals("none", read(DIMENSION_TYPE).get("skybox").getAsString());
    }

    @Test
    void thePocketsRoomsStandInThePocketVoidBiome() throws IOException {
        JsonObject dimension = read(DIMENSION);
        assertEquals("goo:pocket", dimension.get("type").getAsString());
        assertEquals("goo:pocket_void", dimension.getAsJsonObject("generator").getAsJsonObject("settings")
                .get("biome").getAsString());
    }

    private static JsonObject read(String path) throws IOException {
        try (InputStream in = PocketDimensionTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
