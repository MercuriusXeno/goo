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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * en_us.json reads each of the twelve renamed abilities by its short word, and no lang value still reads an old name.
 * decision twelve-abilities-renamed-to-one-short-word
 */
class ShortAbilityNamesTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = ShortAbilityNamesTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @ParameterizedTest
    @MethodSource("renamedKeys")
    void renamedAbilityReadsItsShortWord(String key, String shortWord) {
        assertTrue(lang.has(key), () -> EN_US + " lacks " + key);
        assertEquals(shortWord, lang.get(key).getAsString(), key);
    }

    static Stream<Arguments> renamedKeys() {
        return Stream.of(
                Arguments.of("goo.ability.glow.laser", "Laser"),
                Arguments.of("goo.ability.glow.wisps", "Wisps"),
                Arguments.of("goo.ability.xeno.unveil", "Unveil"),
                Arguments.of("goo.ability.jelly.heal", "Heal"),
                Arguments.of("goo.ability.pulse.extend", "Extend"),
                Arguments.of("goo.ability.pulse.tick", "Tick"),
                Arguments.of("goo.ability.kinetic.remote", "Remote"),
                Arguments.of("goo.ability.rock.gouge", "Gouge"),
                Arguments.of("goo.ability.rock.level", "Level"),
                Arguments.of("goo.ability.shroom.transmit", "Transmit"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sunbeam", "Radiant", "Eldritch Sight", "Vitality", "Extender", "Pulser",
            "Phase Shift", "Telekinesis", "Terrakinesis", "Flatten", "Fungal Shift"})
    void everyLangValueReadsTheNewNameInPlaceOfTheOld(String oldName) {
        List<String> stillOld = lang.entrySet().stream()
                .filter(entry -> entry.getValue().getAsString().contains(oldName))
                .map(Map.Entry::getKey)
                .toList();
        assertEquals(List.of(), stillOld, () -> "lang values still reading " + oldName);
    }
}
