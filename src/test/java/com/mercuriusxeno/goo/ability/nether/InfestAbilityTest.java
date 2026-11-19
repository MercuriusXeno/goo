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
 * Zombie's world goo ships as zombie_infest.json under the display key
 * goo.ability.zombie.infest, which en_us.json reads as Infest with a description.
 * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
 */
class InfestAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String INFEST_JSON = "/data/goo/goo_abilities/zombie_infest.json";
    private static final String INFEST_KEY = "goo.ability.zombie.infest";

    @Test
    void infestReadsInfestWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertNotNull(lang.get(INFEST_KEY), INFEST_KEY);
        assertEquals("Infest", lang.get(INFEST_KEY).getAsString());
        assertNotNull(lang.get(INFEST_KEY + ".description"), INFEST_KEY + ".description");
        assertFalse(lang.get(INFEST_KEY + ".description").getAsString().isBlank(), INFEST_KEY + ".description");
    }

    @Test
    void infestsAbilityNamesTheInfestDisplayKey() throws IOException {
        assertEquals(INFEST_KEY, classpathJson(INFEST_JSON).get("displayName").getAsString());
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = InfestAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
