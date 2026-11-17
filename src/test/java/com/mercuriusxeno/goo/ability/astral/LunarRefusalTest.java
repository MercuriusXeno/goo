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
 * Lunar's refusal, the line a cast looking at neither the moon nor the blue star shows: "That's no moon!".
 * decision lunar-refusal-says-thats-no-moon
 */
class LunarRefusalTest {

    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";
    private static final String REFUSAL = "That's no moon!";

    @Test
    void refusalReadsThatsNoMoon() throws IOException {
        try (InputStream in = LunarRefusalTest.class.getResourceAsStream(EN_US_JSON)) {
            assertNotNull(in, EN_US_JSON + " is not on the classpath");
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(REFUSAL, lang.get(AstralLunar.TELL_REFUSED).getAsString());
        }
    }
}
