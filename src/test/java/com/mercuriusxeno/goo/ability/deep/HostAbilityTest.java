package com.mercuriusxeno.goo.ability.deep;

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
 * Deep's world goo ships as deep_host.json under the display key
 * goo.ability.deep.host, which en_us.json reads as Host with a description.
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 */
class HostAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String HOST_JSON = "/data/goo/goo_abilities/deep_host.json";
    private static final String HOST_KEY = "goo.ability.deep.host";

    @Test
    void hostReadsHostWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertEquals("Host", lang.get(HOST_KEY).getAsString());
        assertNotNull(lang.get(HOST_KEY + ".description"), HOST_KEY + ".description");
        assertFalse(lang.get(HOST_KEY + ".description").getAsString().isBlank(), HOST_KEY + ".description");
    }

    @Test
    void hostsAbilityNamesTheHostDisplayKey() throws IOException {
        assertEquals(HOST_KEY, classpathJson(HOST_JSON).get("displayName").getAsString());
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = HostAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
