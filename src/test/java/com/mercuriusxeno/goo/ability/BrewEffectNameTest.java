package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every bundled type's brew effect reads, in en_us, the name of the brew ability the shipped
 * goo_abilities JSON grants it, or its potion's word for a type granting none.
 * decision brew-effect-reads-the-abilitys-own-name
 */
class BrewEffectNameTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String POTION_PREFIX = "Potion of ";
    private static final String BREW = "Brew";
    private static final String EFFECT_PREFIX = "effect.";

    private static AbilityRegistry shipped;
    private static JsonObject lang;

    @BeforeAll
    static void readShippedAbilitiesAndLang() throws IOException {
        Map<Identifier, AbilityDefinition> abilities = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(AbilityDefinition::id, Function.identity()));
        shipped = new AbilityRegistry(abilities);
        try (InputStream in = BrewEffectNameTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @ParameterizedTest
    @MethodSource("bundledKeys")
    void brewEffectReadsItsAbilitysNameOrItsPotionsWord(ResourceKey<GooTypeDefinition> type) {
        AbilityDefinition granted = shipped.brewAbilityFor(type);
        String expected = granted != null ? translate(granted.displayName())
                : translate("item.minecraft.potion.effect." + GooTypes.id(type) + "_goo").substring(POTION_PREFIX.length());

        String effectName = translate(BrewEffectName.nameKeyOf(type, granted == null ? null : granted.displayName()));

        assertEquals(expected, effectName, () -> GooTypes.id(type) + "'s brew effect");
        assertFalse(effectName.contains(BREW), () -> GooTypes.id(type) + "'s brew effect reads " + effectName);
    }

    @Test
    void noEffectNameInEnUsReadsBrew() {
        lang.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(EFFECT_PREFIX))
                .forEach(entry -> assertFalse(entry.getValue().getAsString().contains(BREW),
                        () -> entry.getKey() + " reads " + entry.getValue().getAsString()));
    }

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeys() {
        return GooTypes.BUNDLED.stream();
    }

    private static String translate(String key) {
        assertTrue(lang.has(key), () -> EN_US + " lacks " + key);
        return lang.get(key).getAsString();
    }
}
