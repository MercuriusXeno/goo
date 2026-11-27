package com.mercuriusxeno.goo.ability.xeno;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Xeno's sculk call ships under the name Shriek: xeno_shriek.json, thrown from
 * xeno goo and shown as goo.ability.xeno.shriek, a world ability whose blob
 * runs one shriek step on its landing, its icon at xeno_shriek.png; no deep_shriek
 * ships (decisions shriek-moves-to-xeno,
 * shriek-is-a-sculk-call-that-draws-every-mob-it-reaches).
 */
class ShriekAbilityTest {

    private static final String SHRIEK_JSON = "data/goo/goo_abilities/xeno_shriek.json";
    private static final String SHRIEK_ICON = "assets/goo/textures/goo/ability/xeno_shriek.png";

    private static JsonObject shriek() throws IOException {
        try (InputStream in = ShriekAbilityTest.class.getClassLoader().getResourceAsStream(SHRIEK_JSON)) {
            assertNotNull(in, SHRIEK_JSON + " should ship");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void shipsAsTheShriekWorldAbility() throws IOException {
        JsonObject shriek = shriek();
        assertEquals("goo.ability.xeno.shriek", shriek.get("displayName").getAsString());
        assertEquals("world", shriek.get("badge").getAsString());
    }

    // decision shriek-blob-cries-once-and-calls-earshot
    @Test
    void runsAShriekStepOnItsLanding() throws IOException {
        List<String> landingSteps = new ArrayList<>();
        for (JsonElement behavior : shriek().getAsJsonArray("behaviors")) {
            landingSteps.add(behavior.getAsJsonObject().get("type").getAsString());
        }
        assertEquals(List.of("shriek"), landingSteps);
    }

    @Test
    void shipsItsIcon() throws IOException {
        try (InputStream in = ShriekAbilityTest.class.getClassLoader().getResourceAsStream(SHRIEK_ICON)) {
            assertNotNull(in, SHRIEK_ICON + " should ship");
        }
    }

    @Test
    void isAXenoAbilityAndNoDeepShriekShips() {
        List<Identifier> shipped = AbilityJson.files().stream().map(AbilityJson::decode).map(AbilityDefinition::id)
                .toList();
        assertEquals(List.of(GooTypes.XENO, false), List.of(AbilityJson.decode("xeno_shriek").gooType(),
                shipped.contains(Identifier.fromNamespaceAndPath("goo", "deep_shriek"))));
    }
}
