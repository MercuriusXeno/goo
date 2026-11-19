package com.mercuriusxeno.goo.ability;

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
 * Astral's world goo reads Dowsing in en_us, with a description beside it,
 * under the display key its shipped ability file names.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 */
class DowsingNameTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String DOWSING_JSON = "/data/goo/goo_abilities/astral_dowsing.json";
    private static final String DISPLAY_KEY = "goo.ability.astral.dowsing";

    @Test
    void dowsingReadsDowsingWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertEquals("Dowsing", lang.get(DISPLAY_KEY).getAsString());
        assertNotNull(lang.get(DISPLAY_KEY + ".description"), DISPLAY_KEY + ".description");
        assertFalse(lang.get(DISPLAY_KEY + ".description").getAsString().isBlank());
    }

    @Test
    void dowsingsAbilityFileNamesItsDisplayKey() throws IOException {
        assertEquals(DISPLAY_KEY, classpathJson(DOWSING_JSON).get("displayName").getAsString());
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = DowsingNameTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
