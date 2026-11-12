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
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The black hole loads as Void's ability named Dark Star, last on Void's wheel, Hole is the one
 * Void ability named Hole, and Nether ships Decay and Undead alone; reads the shipped ability
 * files and en_us.json through the classpath.
 * decision anti-moves-from-nether-to-void
 * decision black-hole-is-renamed-dark-star
 */
class BlackHoleIsVoidsTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String DARK_STAR = "void_dark_star";
    /** The name the ability stood under before it read Dark Star. */
    private static final String OLD_NAME = "black_hole";
    private static final String VOID_KEY_PREFIX = "goo.ability.void.";
    private static final String NAME_KEY = "goo.ability.void.dark_star";
    private static final String HOLE_KEY = "goo.ability.void.hole";
    /** After Erase 0, Hole 10, Outsider 20 and the Vacuum another thread adds at 30. */
    private static final int WHEEL_ORDER = 40;
    private static final String ICON = "/assets/goo/textures/goo/ability/void_dark_star.png";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = BlackHoleIsVoidsTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void darkStarLoadsAsAVoidAbilityLastOnItsWheel() {
        AbilityDefinition darkStar = AbilityJson.decode(DARK_STAR);

        assertEquals(GooTypes.VOID, darkStar.gooType());
        assertEquals(NAME_KEY, darkStar.displayName());
        assertEquals(WHEEL_ORDER, darkStar.order());
        assertNotNull(BlackHoleIsVoidsTest.class.getResource(ICON), ICON);
    }

    @Test
    void shippedAbilityFilesHoldDarkStarInPlaceOfBlackHole() {
        Set<String> names = AbilityJson.files().stream()
                .map(Path::getFileName).map(Path::toString)
                .collect(Collectors.toSet());

        assertTrue(names.contains(DARK_STAR + ".json"), DARK_STAR + ".json");
        assertEquals(List.of(), names.stream().filter(name -> name.contains(OLD_NAME)).toList(),
                "ability files still naming the black hole");
    }

    @Test
    void darkStarReadsDarkStarUnderVoidsKey() {
        assertEquals("Dark Star", lang.get(NAME_KEY).getAsString());
        String description = lang.get(NAME_KEY + ".description").getAsString();
        assertTrue(description.contains("dark star"), description);
        assertFalse(description.toLowerCase(Locale.ROOT).contains("black hole"), description);
        List<String> old = lang.keySet().stream().filter(key -> key.startsWith(VOID_KEY_PREFIX + OLD_NAME)).toList();
        assertEquals(List.of(), old, "lang keys still naming the black hole");
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
