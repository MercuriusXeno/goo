package com.mercuriusxeno.goo.ability.hex;

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
 * Hex's world goo ships as hex_leech.json under the display key
 * goo.ability.hex.leech, which en_us.json reads as Leech with a description.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 */
class LeechAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String LEECH_JSON = "/data/goo/goo_abilities/hex_leech.json";
    private static final String LEECH_KEY = "goo.ability.hex.leech";

    @Test
    void leechReadsLeechWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertNotNull(lang.get(LEECH_KEY), LEECH_KEY);
        assertEquals("Leech", lang.get(LEECH_KEY).getAsString());
        assertNotNull(lang.get(LEECH_KEY + ".description"), LEECH_KEY + ".description");
        assertFalse(lang.get(LEECH_KEY + ".description").getAsString().isBlank(), LEECH_KEY + ".description");
    }

    @Test
    void leechsAbilityNamesTheLeechDisplayKey() throws IOException {
        assertEquals(LEECH_KEY, classpathJson(LEECH_JSON).get("displayName").getAsString());
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = LeechAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
