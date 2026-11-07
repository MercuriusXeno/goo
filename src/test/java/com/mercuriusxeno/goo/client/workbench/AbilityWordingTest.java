package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.workbench.AbilitiesModel;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every price field the shipped abilities hold reads a plain label and a one-sentence description from en_us.json,
 * read off the classpath, and the lang holds no param or mechanism key a price field reads not; a key the lang holds
 * not falls back to the raw path (decision ability-params-read-named-under-their-mechanism-and-hover-a-description).
 */
class AbilityWordingTest {
    private static final String LANG = "assets/goo/lang/en_us.json";

    private static JsonObject lang() throws IOException {
        try (InputStream in = AbilityWordingTest.class.getClassLoader().getResourceAsStream(LANG)) {
            assertNotNull(in, "the classpath holds no " + LANG);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    // Each shipped price field as the tab reads it, its file's name before it.
    private static Map<String, AbilitiesTab.Leaf> shippedPrices() throws IOException {
        Map<String, AbilitiesTab.Leaf> prices = new LinkedHashMap<>();
        for (Path file : AbilityJson.files()) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (AbilitiesModel.Leaf leaf : AbilitiesModel.leaves(Identifier.fromNamespaceAndPath("goo", name),
                    json)) {
                AbilitiesTab.Leaf read = new AbilitiesTab.Leaf(leaf.path(), "", AbilitiesTab.Kind.NUMBER,
                        leaf.mechanism(), leaf.field());
                if (read.prices()) {
                    prices.put(name + ":" + leaf.path(), read);
                }
            }
        }
        return prices;
    }

    @Test
    void everyShippedPriceFieldsLabelAndDescriptionStandInTheLang() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, AbilitiesTab.Leaf> price : shippedPrices().entrySet()) {
            AbilitiesTab.Leaf leaf = price.getValue();
            for (String key : List.of(AbilityWording.labelKey(leaf.mechanism(), leaf.field()),
                    AbilityWording.descriptionKey(leaf.mechanism(), leaf.field()))) {
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    missing.add(price.getKey() + " " + key);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void theLangHoldsNoParamOrMechanismKeyAPriceFieldReadsNot() throws IOException {
        Set<String> read = new HashSet<>();
        for (AbilitiesTab.Leaf leaf : shippedPrices().values()) {
            read.add(AbilityWording.labelKey(leaf.mechanism(), leaf.field()));
            read.add(AbilityWording.descriptionKey(leaf.mechanism(), leaf.field()));
        }
        List<String> unread = lang().keySet().stream()
                .filter(key -> key.startsWith("goo.ability.param.") || key.startsWith("goo.ability.mechanism."))
                .filter(key -> !read.contains(key)).toList();
        assertEquals(List.of(), unread);
    }

    @Test
    void aKeyTheLangHoldsNotReadsTheRawPath() {
        AbilitiesTab.Leaf leaf = new AbilitiesTab.Leaf("behaviors/1/range", "16", AbilitiesTab.Kind.NUMBER, "grab",
                "range");
        assertEquals("behaviors/1/range", AbilityWording.label(leaf, key -> key));
        assertEquals("Range", AbilityWording.label(leaf, key -> key.equals("goo.ability.param.grab.range") ? "Range"
                : key));
    }
}
