package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped ability's key, its file id less its goo type prefix, spells its shown name lowercased with spaces as
 * underscores, its displayName lang key ends in that key, and an icon of its id stands, so a key cannot drift from the
 * name the game shows.
 * decision rename-rekeys-everything-that-names-the-ability
 */
class AbilityKeysFollowShownNamesTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String ABILITY_ICON = "/assets/goo/textures/goo/ability/%s.png";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = AbilityKeysFollowShownNamesTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static Stream<Path> shippedAbilities() {
        return AbilityJson.files().stream();
    }

    @ParameterizedTest
    @MethodSource("shippedAbilities")
    void keyLangKeyAndIconFollowTheShownName(Path file) throws IOException {
        String id = file.getFileName().toString().replaceFirst("\\.json$", "");
        JsonObject ability = JsonParser.parseString(AbilityJson.read(id)).getAsJsonObject();
        String prefix = ability.get("gooType").getAsString() + "_";
        assertTrue(id.startsWith(prefix), () -> id + " does not start with its goo type prefix " + prefix);
        String key = id.substring(prefix.length());
        String displayName = ability.get("displayName").getAsString();
        assertTrue(lang.has(displayName), () -> id + " shows " + displayName + ", which en_us.json lacks");
        String shownName = lang.get(displayName).getAsString();
        assertEquals(shownName.toLowerCase(Locale.ROOT).replace(' ', '_'), key,
                () -> id + " is keyed " + key + " but shown as " + shownName);
        assertTrue(displayName.endsWith("." + key),
                () -> id + " is keyed " + key + " but its lang key reads " + displayName);
        assertNotNull(AbilityKeysFollowShownNamesTest.class.getResource(ABILITY_ICON.formatted(id)),
                () -> id + " is keyed " + key + " and shown as " + shownName + " but ships no icon");
    }
}
