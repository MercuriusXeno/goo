package com.mercuriusxeno.goo.ability.astral;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.HeldRoute;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped Lunar ability, astral's moon trip: it ships under the key astral_lunar, shows the name "Lunar", carries
 * its icon and its astral_lunar behavior, and runs on every tick right click is held, so holding pulls the moon in
 * and letting go stops the pull.
 * decision astral-gate-is-renamed-lunar
 * decision holding-right-click-pulls-the-moon-and-early-release-cancels
 */
class AstralLunarAbilityTest {

    private static final String KEY = "astral_lunar";
    private static final String ABILITY_FILE = "/data/goo/goo_abilities/" + KEY + ".json";
    private static final String ICON = "/assets/goo/textures/goo/ability/" + KEY + ".png";
    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";
    private static final String SHOWN_NAME = "Lunar";

    @Test
    void abilityShipsUnderTheKeyAstralLunarShownAsLunar() throws IOException {
        JsonObject ability = readJson(ABILITY_FILE);
        String nameKey = ability.get("displayName").getAsString();
        assertEquals(SHOWN_NAME, readJson(EN_US_JSON).get(nameKey).getAsString(), nameKey + " reads no Lunar");
    }

    @Test
    void abilityCarriesItsIconAndItsAstralLunarBehavior() throws IOException {
        try (InputStream icon = AstralLunarAbilityTest.class.getResourceAsStream(ICON)) {
            assertNotNull(icon, ICON + " is not on the classpath");
        }
        String behavior = readJson(ABILITY_FILE).getAsJsonArray("behaviors").get(0).getAsJsonObject()
                .get("type").getAsString();
        assertEquals(KEY, behavior);
    }

    @Test
    void lunarRunsWhileRightClickIsHeld() {
        AbilityDefinition lunar = AbilityJson.decode(KEY);
        assertTrue(HeldRoute.runsWhileHeld(lunar.delivery(), lunar.badge()),
                "Lunar wears " + lunar.badge() + " on " + lunar.delivery() + ", which runs once rather than held");
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (InputStream in = AstralLunarAbilityTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
