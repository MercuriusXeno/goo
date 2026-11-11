package com.mercuriusxeno.goo.ability.hole;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Void's hole opens for a fixed cost, priced by no formula, and the Pocket screen's lang keys are gone with it.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
class VoidHoleCostTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";

    @Test
    void holeCostsAFixedAmountWithNoFormula() {
        JsonObject hole = JsonParser.parseString(AbilityJson.read("void_hole")).getAsJsonObject();
        assertTrue(hole.get("cost").getAsJsonPrimitive().isNumber(), () -> "cost reads " + hole.get("cost"));
        assertTrue(hole.get("cost").getAsInt() > 0, () -> "cost reads " + hole.get("cost"));
        assertFalse(hole.has("cost_formula"), () -> "cost_formula reads " + hole.get("cost_formula"));
    }

    @Test
    void langHoldsNoPocketScreenKey() throws IOException {
        try (InputStream in = VoidHoleCostTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : lang.keySet()) {
                assertFalse(key.startsWith("goo.pocket."), () -> "en_us still holds " + key);
            }
        }
    }
}
