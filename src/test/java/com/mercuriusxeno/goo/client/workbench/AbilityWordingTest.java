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
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every param the shipped abilities hold reads a plain label and a one-sentence description from en_us.json, under a
 * mechanism heading from the same file, read off the classpath; a key the lang holds not falls back to the raw path
 * (decision ability-params-read-named-under-their-mechanism-and-hover-a-description).
 */
class AbilityWordingTest {
    private static final String LANG = "assets/goo/lang/en_us.json";

    private static JsonObject lang() throws IOException {
        try (InputStream in = AbilityWordingTest.class.getClassLoader().getResourceAsStream(LANG)) {
            assertNotNull(in, "the classpath holds no " + LANG);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyShippedParamsLabelDescriptionAndMechanismStandInTheLang() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Path file : AbilityJson.files()) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (AbilitiesModel.Leaf leaf : AbilitiesModel.leaves(Identifier.fromNamespaceAndPath("goo", name),
                    json)) {
                for (String key : List.of(AbilityWording.labelKey(leaf.mechanism(), leaf.field()),
                        AbilityWording.descriptionKey(leaf.mechanism(), leaf.field()),
                        AbilityWording.mechanismKey(leaf.mechanism()))) {
                    if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                        missing.add(name + ":" + leaf.path() + " " + key);
                    }
                }
            }
        }
        assertEquals(List.of(), missing.stream().distinct().toList());
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
