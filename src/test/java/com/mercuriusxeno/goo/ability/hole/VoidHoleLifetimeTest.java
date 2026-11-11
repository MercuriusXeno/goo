package com.mercuriusxeno.goo.ability.hole;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Void's hole carries its open lifetime and the time left a swallow floors it at as data, and its
 * description says it stands open temporarily, naming no duration.
 * decision hole-holds-twelve-seconds-and-each-throw-in-floors-six
 */
class VoidHoleLifetimeTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String DESCRIPTION = "goo.ability.void.hole.description";
    /** Twelve seconds of ticks. */
    private static final int LIFETIME = 240;
    /** Six seconds of ticks. */
    private static final int REFRESH = 120;

    @Test
    void holeOpensForTwelveSecondsAndASwallowFloorsSix() {
        JsonObject hole = JsonParser.parseString(AbilityJson.read("void_hole")).getAsJsonObject();
        JsonObject open = hole.getAsJsonArray("behaviors").get(0).getAsJsonObject();

        assertEquals("hole_open", open.get("type").getAsString());
        assertEquals(LIFETIME, open.get("lifetime").getAsInt());
        assertEquals(REFRESH, open.get("refresh").getAsInt());
    }

    @Test
    void descriptionSaysTemporarilyAndNamesNoDuration() throws IOException {
        try (InputStream in = VoidHoleLifetimeTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String description = lang.get(DESCRIPTION).getAsString();

            assertTrue(description.contains("temporarily"), description);
            assertFalse(description.chars().anyMatch(Character::isDigit), description);
            assertFalse(description.contains("second"), description);
        }
    }
}
