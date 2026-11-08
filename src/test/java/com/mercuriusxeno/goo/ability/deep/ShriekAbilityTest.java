package com.mercuriusxeno.goo.ability.deep;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep's sculk call ships under the name Shriek: deep_shriek.json, shown as
 * goo.ability.deep.shriek, a world ability whose blob lingers running a
 * shriek step, its icon at deep_shriek.png
 * (decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches).
 */
class ShriekAbilityTest {

    private static final String SHRIEK_JSON = "data/goo/goo_abilities/deep_shriek.json";
    private static final String SHRIEK_ICON = "assets/goo/textures/goo/ability/deep_shriek.png";

    private static JsonObject shriek() throws IOException {
        try (InputStream in = ShriekAbilityTest.class.getClassLoader().getResourceAsStream(SHRIEK_JSON)) {
            assertNotNull(in, SHRIEK_JSON + " should ship");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void shipsAsTheShriekWorldAbility() throws IOException {
        JsonObject shriek = shriek();
        assertEquals("goo.ability.deep.shriek", shriek.get("displayName").getAsString());
        assertEquals("world", shriek.get("badge").getAsString());
    }

    @Test
    void lingersRunningAShriekStep() throws IOException {
        boolean runsShriek = false;
        for (JsonElement behavior : shriek().getAsJsonArray("behaviors")) {
            JsonObject linger = behavior.getAsJsonObject();
            if (!"linger".equals(linger.get("type").getAsString())) {
                continue;
            }
            for (JsonElement step : linger.getAsJsonArray("steps")) {
                runsShriek |= "shriek".equals(step.getAsJsonObject().get("type").getAsString());
            }
        }
        assertTrue(runsShriek, "deep_shriek.json should linger running a shriek step");
    }

    @Test
    void shipsItsIcon() throws IOException {
        try (InputStream in = ShriekAbilityTest.class.getClassLoader().getResourceAsStream(SHRIEK_ICON)) {
            assertNotNull(in, SHRIEK_ICON + " should ship");
        }
    }
}
