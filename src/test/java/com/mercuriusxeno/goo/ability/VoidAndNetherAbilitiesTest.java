package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Hole is the one Void ability named Hole, and Nether ships Decay and Undead alone; reads the
 * shipped ability files and en_us.json through the classpath.
 * decision anti-moves-from-nether-to-void
 */
class VoidAndNetherAbilitiesTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String VOID_KEY_PREFIX = "goo.ability.void.";
    private static final String HOLE_KEY = "goo.ability.void.hole";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = VoidAndNetherAbilitiesTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void holeIsTheOneVoidAbilityNamedHole() {
        Map<String, String> namedHole = lang.keySet().stream()
                .filter(key -> key.startsWith(VOID_KEY_PREFIX))
                .filter(key -> key.indexOf('.', VOID_KEY_PREFIX.length()) < 0)
                .filter(key -> lang.get(key).getAsString().contains("Hole"))
                .collect(Collectors.toMap(key -> key, key -> lang.get(key).getAsString()));

        assertEquals(Map.of(HOLE_KEY, "Hole"), namedHole);
    }

    @Test
    void netherShipsDecayAndUndead() {
        Set<String> nether = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.gooType() == GooTypes.NETHER)
                .map(ability -> ability.id().getPath())
                .collect(Collectors.toSet());

        assertEquals(Set.of("nether_decay", "nether_undead"), nether);
    }
}
