package com.mercuriusxeno.goo.ability.zone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ender's goo.ability.ender.elude entry reads Elude in en_us, its description tells the random
 * safe-spot escape from mob and projectile hits, and no en_us value still reads Shifter.
 * decision shifter-is-renamed-elude
 */
class EludeNameTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String NAME_KEY = "goo.ability.ender.elude";
    private static final String DESCRIPTION_KEY = NAME_KEY + ".description";
    private static final List<String> DESCRIPTION_WORDS = List.of("mob", "projectile", "random", "safe");

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = EludeNameTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void enderEludeKeyReadsElude() {
        assertEquals("Elude", lang.get(NAME_KEY).getAsString());
    }

    @Test
    void descriptionTellsTheRandomSafeEscapeFromMobAndProjectileHits() {
        String description = lang.get(DESCRIPTION_KEY).getAsString().toLowerCase(Locale.ROOT);
        for (String word : DESCRIPTION_WORDS) {
            assertTrue(description.contains(word), () -> DESCRIPTION_KEY + " lacks " + word + ": " + description);
        }
    }

    @Test
    void everyLangValueReadsWithoutShifter() {
        for (Map.Entry<String, JsonElement> entry : lang.entrySet()) {
            String value = entry.getValue().getAsString();
            assertTrue(!value.toLowerCase(Locale.ROOT).contains("shifter"), () -> entry.getKey() + " reads " + value);
        }
    }
}
