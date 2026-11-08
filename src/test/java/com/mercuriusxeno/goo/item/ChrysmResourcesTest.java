package com.mercuriusxeno.goo.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Each chrysm tier's name has a lang entry (decision chrysm-tiers-fixed-and-stackable).
 * Reads the resources through the classpath.
 */
class ChrysmResourcesTest {

    @ParameterizedTest
    @EnumSource(ChrysmTier.class)
    void nameTakesTheTypeBeforeTheTier(ChrysmTier tier) throws Exception {
        JsonObject lang = readJson("/assets/goo/lang/en_us.json");
        StringBuilder tierWords = new StringBuilder();
        for (String word : tier.registryPath().split("_")) {
            tierWords.append(' ').append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        assertEquals("%s" + tierWords, lang.get(tier.translationKey()).getAsString());
    }

    private static JsonObject readJson(String path) throws Exception {
        try (InputStream in = ChrysmResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Resource missing on classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
