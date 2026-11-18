package com.mercuriusxeno.goo.ability.astral;

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
 * Lunar's refusals, the lines a cast holding neither the moon nor the blue planet shows: "That's no moon!" in the
 * overworld and "That's not your world" on the moon.
 * decision lunar-refusal-says-thats-no-moon
 * decision terran-refusal-says-thats-not-your-world
 */
class LunarRefusalTest {

    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";
    private static final String REFUSAL = "That's no moon!";
    private static final String TERRAN_REFUSAL = "That's not your world";

    private static JsonObject lang() throws IOException {
        try (InputStream in = LunarRefusalTest.class.getResourceAsStream(EN_US_JSON)) {
            assertNotNull(in, EN_US_JSON + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String read(JsonObject lang, String key) {
        return lang.has(key) ? lang.get(key).getAsString() : null;
    }

    @Test
    void refusalReadsThatsNoMoon() throws IOException {
        assertEquals(REFUSAL, read(lang(), LunarSight.LUNAR_REFUSED));
    }

    @Test
    void terranRefusalReadsThatsNotYourWorld() throws IOException {
        assertEquals(TERRAN_REFUSAL, read(lang(), LunarSight.TERRAN_REFUSED));
    }
}
