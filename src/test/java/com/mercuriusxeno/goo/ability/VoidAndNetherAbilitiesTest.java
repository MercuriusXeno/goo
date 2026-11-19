package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
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
 * Hole is the one Void ability named Hole, Nether ships Infest and Tartarus, and Zombie ships Decay and Undead;
 * reads the shipped ability files and en_us.json through the classpath.
 * decision anti-moves-from-nether-to-void
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
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

    /**
     * Infest moves to zombie on its own thread; Tartarus stays on nether, and Invert joins it.
     * decision zombie-goo-is-the-undead-and-takes-the-decay-abilities
     * decision nether-keeps-its-name-and-takes-inversion-as-its-theme
     */
    @Test
    void netherShipsInfestTartarusAndInvert() {
        assertEquals(Set.of("nether_infest", "nether_tartarus", "nether_invert"), shippedPathsOf(GooTypes.NETHER));
    }

    /**
     * decision zombie-goo-is-the-undead-and-takes-the-decay-abilities
     */
    @Test
    void zombieShipsDecayAndUndead() {
        assertEquals(Set.of("zombie_decay", "zombie_undead"), shippedPathsOf(GooTypes.ZOMBIE));
    }

    private static Set<String> shippedPathsOf(ResourceKey<GooTypeDefinition> type) {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.gooType() == type)
                .map(ability -> ability.id().getPath())
                .collect(Collectors.toSet());
    }
}
