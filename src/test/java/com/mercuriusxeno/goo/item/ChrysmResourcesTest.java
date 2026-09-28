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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each chrysm tier's item definition draws the quartz crystal at its own tier through
 * the goo:chrysm_crystal special model, and its name has a lang entry (decision
 * chrysm-tiers-fixed-and-stackable).
 * Reads the resources through the classpath.
 */
class ChrysmResourcesTest {

    private static final String GOO_NAMESPACE = "goo:";

    @ParameterizedTest
    @EnumSource(ChrysmTier.class)
    void itemDefinitionDrawsTheCrystalAtItsTier(ChrysmTier tier) throws Exception {
        JsonObject definition = readJson("/assets/goo/items/" + tier.registryPath() + ".json").getAsJsonObject("model");
        assertEquals("minecraft:special", definition.get("type").getAsString());
        JsonObject special = definition.getAsJsonObject("model");
        assertEquals("goo:chrysm_crystal", special.get("type").getAsString());
        assertEquals(tier.registryPath(), special.get("tier").getAsString());
        String base = definition.get("base").getAsString();
        assertTrue(base.startsWith(GOO_NAMESPACE), base + " should be a goo model");
        readJson("/assets/goo/models/" + base.substring(GOO_NAMESPACE.length()) + ".json");
    }

    @ParameterizedTest
    @EnumSource(ChrysmTier.class)
    void nameTakesTheTypeBeforeTheTier(ChrysmTier tier) throws Exception {
        JsonObject lang = readJson("/assets/goo/lang/en_us.json");
        String tierWord = Character.toUpperCase(tier.registryPath().charAt(0)) + tier.registryPath().substring(1);
        assertEquals("%s " + tierWord, lang.get(tier.translationKey()).getAsString());
    }

    private static JsonObject readJson(String path) throws Exception {
        try (InputStream in = ChrysmResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Resource missing on classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
