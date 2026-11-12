package com.mercuriusxeno.goo.ability.pocket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Void's item storage ability ships as goo:void_hole, shown as Hole with a description
 * reading hole and its former pouch icon, and the goo:void_pocket file and lang keys are gone.
 * decision void-pocket-is-renamed-hole
 */
class HoleNameTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String HOLE_KEY = "goo.ability.void.hole";
    private static final String POCKET_KEY = "goo.ability.void.pocket";
    private static final String HOLE_ICON = "/assets/goo/textures/goo/ability/void_hole.png";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = HoleNameTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void holeFileCarriesTheHoleDisplayName() {
        JsonObject hole = JsonParser.parseString(AbilityJson.read("void_hole")).getAsJsonObject();
        assertEquals(HOLE_KEY, hole.get("displayName").getAsString());
    }

    @Test
    void pocketFileNoLongerShips() {
        String resource = AbilityJson.ABILITIES_DIR + "/void_pocket.json";
        assertNull(HoleNameTest.class.getClassLoader().getResource(resource), resource);
    }

    @Test
    void holeKeyReadsHole() {
        assertEquals("Hole", lang.get(HOLE_KEY).getAsString());
    }

    @Test
    void holeDescriptionReadsHole() {
        String description = lang.get(HOLE_KEY + ".description").getAsString().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("hole"), () -> HOLE_KEY + ".description reads " + description);
    }

    @Test
    void langHoldsNoVoidPocketAbilityKey() {
        for (String key : lang.keySet()) {
            assertFalse(key.startsWith(POCKET_KEY), () -> "en_us still holds " + key);
        }
    }

    @Test
    void holeIconStandsAtTheConventionPath() {
        assertNotNull(HoleNameTest.class.getResource(HOLE_ICON), HOLE_ICON);
    }
}
