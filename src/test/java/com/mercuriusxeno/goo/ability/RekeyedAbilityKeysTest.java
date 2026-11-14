package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Each rekeyed ability ships under the key its shown name spells, with its lang entry and icon, and its old key ships nothing.
 * decision eighteen-divergent-keys-follow-their-shown-names
 */
class RekeyedAbilityKeysTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String ABILITY_JSON = "/data/goo/goo_abilities/%s.json";
    private static final String ABILITY_ICON = "/assets/goo/textures/goo/ability/%s.png";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        lang = readJson(EN_US);
    }

    @ParameterizedTest
    @MethodSource("rekeyedAbilities")
    void rekeyedAbilityShipsUnderItsShownName(String id, String shownName) throws IOException {
        JsonObject ability = readJson(ABILITY_JSON.formatted(id));
        String displayName = ability.get("displayName").getAsString();
        assertEquals("goo.ability." + id.replaceFirst("_", "."), displayName, id);
        assertEquals(shownName, lang.get(displayName).getAsString(), displayName);
        assertNotNull(lang.get(displayName + ".description"), displayName + ".description");
        assertNotNull(RekeyedAbilityKeysTest.class.getResource(ABILITY_ICON.formatted(id)), id + " icon");
    }

    static Stream<Arguments> rekeyedAbilities() {
        return Stream.of(
                Arguments.of("glow_bulb", "Bulb"),
                Arguments.of("glow_wisps", "Wisps"),
                Arguments.of("glow_laser", "Laser"),
                Arguments.of("rock_level", "Level"),
                Arguments.of("rock_gouge", "Gouge"),
                Arguments.of("metal_dart", "Dart"),
                Arguments.of("metal_urchin", "Urchin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"glow_crystal", "glow_radiant", "glow_sunbeam",
            "rock_flatten", "rock_terrakinesis", "metal_javelin", "metal_spikes"})
    void oldKeyShipsNoAbility(String oldId) {
        assertNull(RekeyedAbilityKeysTest.class.getResource(ABILITY_JSON.formatted(oldId)), oldId + " json");
        assertNull(RekeyedAbilityKeysTest.class.getResource(ABILITY_ICON.formatted(oldId)), oldId + " icon");
        assertNull(lang.get("goo.ability." + oldId.replaceFirst("_", ".")), oldId + " lang");
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (InputStream in = RekeyedAbilityKeysTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
