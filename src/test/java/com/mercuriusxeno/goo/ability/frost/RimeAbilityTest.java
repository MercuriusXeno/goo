package com.mercuriusxeno.goo.ability.frost;

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
 * Frost's world goo ships as frost_rime.json under the display key
 * goo.ability.frost.rime, which en_us.json reads as Rime with a description.
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 */
class RimeAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String RIME_JSON = "/data/goo/goo_abilities/frost_rime.json";
    private static final String RIME_KEY = "goo.ability.frost.rime";
    private static final String DESCRIPTION_KEY = RIME_KEY + ".description";

    @Test
    void rimeReadsRimeWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertEquals("Rime", lang.has(RIME_KEY) ? lang.get(RIME_KEY).getAsString() : null);
        assertNotNull(lang.get(DESCRIPTION_KEY), DESCRIPTION_KEY);
        assertFalse(lang.get(DESCRIPTION_KEY).getAsString().isBlank(), DESCRIPTION_KEY);
    }

    @Test
    void rimesAbilityNamesTheRimeDisplayKey() throws IOException {
        assertEquals(RIME_KEY, classpathJson(RIME_JSON).get("displayName").getAsString());
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = RimeAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
