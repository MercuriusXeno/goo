package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.data.GooFraction;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped ability's description key, and every cost_mechanics key a
 * shipped ability names, resolves to text in en_us.json, and each
 * cost-mechanics text fills with no placeholder left.
 * decisions ability-json-carries-a-description, zero-cost-field-formats-the-cost-mechanics
 */
class AbilityTextsLangTest {

    private static final String LANG_PATH = "/assets/goo/lang/en_us.json";
    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = AbilityTextsLangTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(in, LANG_PATH);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    /** The ability the icon thread deletes, which takes no description here. */
    private static final String RETIRING = "goo:xeno_mutate";

    static Stream<AbilityDefinition> shippedAbilities() {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(definition -> !RETIRING.equals(definition.id().toString()));
    }

    static Stream<AbilityDefinition> abilitiesNamingCostMechanics() {
        return shippedAbilities().filter(definition -> definition.costMechanics().isPresent());
    }

    private static String valueOf(String key) {
        return lang.has(key) ? lang.get(key).getAsString() : "";
    }

    @ParameterizedTest
    @MethodSource("shippedAbilities")
    void descriptionKeyResolvesToText(AbilityDefinition definition) {
        assertFalse(valueOf(definition.description()).isBlank(), definition.description());
    }

    @ParameterizedTest
    @MethodSource("abilitiesNamingCostMechanics")
    void costMechanicsKeyResolvesToText(AbilityDefinition definition) {
        String key = definition.costMechanics().orElseThrow();

        assertFalse(valueOf(key).isBlank(), key);
    }

    @ParameterizedTest
    @MethodSource("abilitiesNamingCostMechanics")
    void costMechanicsTextFillsWithNoPlaceholderLeft(AbilityDefinition definition) {
        ClientAbility client = ClientAbility.fromEntry(
                AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition)).getFirst());
        CostMechanicsLookup enUs = new CostMechanicsLookup(AbilityTextsLangTest::valueOf, item -> GooFraction.of(1));

        String text = client.costMechanics(enUs).orElseThrow();

        assertFalse(text.isBlank(), definition.id().toString());
        assertTrue(!text.contains("%"), definition.id() + ": " + text);
    }
}
