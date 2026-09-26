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
 * Each chrysm tier's item definition draws a model whose texture ships, tinted by
 * goo:goo_type, and its name has a lang entry (decision chrysm-tiers-fixed-and-stackable).
 * Reads the resources through the classpath.
 */
class ChrysmResourcesTest {

    private static final String GOO_NAMESPACE = "goo:";

    @ParameterizedTest
    @EnumSource(ChrysmTier.class)
    void itemDefinitionDrawsAShippedTextureTintedByType(ChrysmTier tier) throws Exception {
        JsonObject definition = readJson("/assets/goo/items/" + tier.registryPath() + ".json").getAsJsonObject("model");
        assertEquals("goo:goo_type",
                definition.getAsJsonArray("tints").get(0).getAsJsonObject().get("type").getAsString());
        String model = definition.get("model").getAsString();
        assertTrue(model.startsWith(GOO_NAMESPACE), model + " should be a goo model");
        String texture = readJson("/assets/goo/models/" + model.substring(GOO_NAMESPACE.length()) + ".json")
                .getAsJsonObject("textures").get("layer0").getAsString();
        assertTrue(texture.startsWith(GOO_NAMESPACE), texture + " should be a goo texture");
        String path = "/assets/goo/textures/" + texture.substring(GOO_NAMESPACE.length()) + ".png";
        try (InputStream in = ChrysmResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Texture missing on classpath: " + path);
        }
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
