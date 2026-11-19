package com.mercuriusxeno.goo.ability.nether;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Nether's Gloom ships as nether_gloom.json under the display key
 * goo.ability.nether.gloom, which en_us.json reads as Gloom with a description.
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 */
class GloomAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String GLOOM_JSON = "/data/goo/goo_abilities/nether_gloom.json";
    private static final String GLOOM_KEY = "goo.ability.nether.gloom";

    @Test
    void gloomReadsGloomWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertNotNull(lang.get(GLOOM_KEY), GLOOM_KEY);
        assertEquals("Gloom", lang.get(GLOOM_KEY).getAsString());
        assertNotNull(lang.get(GLOOM_KEY + ".description"), GLOOM_KEY + ".description");
        assertFalse(lang.get(GLOOM_KEY + ".description").getAsString().isBlank(), GLOOM_KEY + ".description");
    }

    @Test
    void gloomsAbilityNamesTheGloomDisplayKey() throws IOException {
        assertEquals(GLOOM_KEY, classpathJson(GLOOM_JSON).get("displayName").getAsString());
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = GloomAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
