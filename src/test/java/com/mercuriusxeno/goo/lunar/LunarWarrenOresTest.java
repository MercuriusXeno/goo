package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eldritch warrens hold ancient debris richer than the nether does, its feature's count per chunk times its vein
 * size standing over the nether's two debris features together, and place no diamond or gold ore, opulent things
 * having no place on lunar.
 * decision lunar-rock-holds-diamond-gold-and-ancient-debris
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
class LunarWarrenOresTest {

    private static final String WARREN_BIOME = "/data/goo/worldgen/biome/lunar_warrens.json";
    private static final String COUNT = "minecraft:count";
    private static final String WARREN_DEBRIS = "goo:ore_lunar_ancient_debris";

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = LunarWarrenOresTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String worldgenPath(String id, String kind) {
        String[] parts = id.split(":");
        return "/data/" + parts[0] + "/worldgen/" + kind + "/" + parts[1] + ".json";
    }

    // 26.3 keeps a feature under worldgen/feature with its settings at its top level, where 26.2 nested them in config.
    private static JsonObject featureOf(String placedId) throws IOException {
        JsonObject placed = resourceJson(worldgenPath(placedId, "placed_feature"));
        return resourceJson(worldgenPath(placed.get("feature").getAsString(), "feature"));
    }

    // A target's state reads as a block id alone or as the block with its properties.
    private static String blockOf(JsonElement state) {
        return state.isJsonPrimitive() ? state.getAsString() : state.getAsJsonObject().get("Name").getAsString();
    }

    /** A placed feature's veins per chunk times its feature's vein size. */
    static int yieldOf(String placedId) throws IOException {
        JsonObject placed = resourceJson(worldgenPath(placedId, "placed_feature"));
        int count = 1;
        for (JsonElement step : placed.getAsJsonArray("placement")) {
            JsonObject modifier = step.getAsJsonObject();
            if (COUNT.equals(modifier.get("type").getAsString())) {
                count *= modifier.get("count").getAsInt();
            }
        }
        return count * featureOf(placedId).get("size").getAsInt();
    }

    private static List<String> warrenFeatures() throws IOException {
        List<String> features = new ArrayList<>();
        for (JsonElement step : resourceJson(WARREN_BIOME).getAsJsonArray("features")) {
            step.getAsJsonArray().forEach(feature -> features.add(feature.getAsString()));
        }
        return features;
    }

    /** The block states an ore feature's targets place, or none for a feature with no targets. */
    private static List<String> placedStates(String placedId) throws IOException {
        JsonObject feature = featureOf(placedId);
        List<String> states = new ArrayList<>();
        if (feature.has("targets")) {
            feature.getAsJsonArray("targets").forEach(target -> states.add(
                    blockOf(target.getAsJsonObject().get("state"))));
        }
        return states;
    }

    @Test
    void warrenAncientDebrisYieldsMoreThanTheNethersTwoFeatures() throws IOException {
        assertTrue(warrenFeatures().contains(WARREN_DEBRIS), WARREN_BIOME + " names no " + WARREN_DEBRIS);
        int nether = yieldOf("minecraft:ore_debris_small") + yieldOf("minecraft:ore_ancient_debris_large");
        assertTrue(yieldOf(WARREN_DEBRIS) > nether, WARREN_DEBRIS + " yields " + yieldOf(WARREN_DEBRIS)
                + ", the nether " + nether);
    }

    /** Every ore the warrens place is something other than diamond or gold ore. */
    @Test
    void theWarrensPlaceNoDiamondOrGoldOre() throws IOException {
        List<String> opulent = new ArrayList<>();
        for (String feature : warrenFeatures()) {
            for (String state : placedStates(feature)) {
                if (state.endsWith("diamond_ore") || state.endsWith("gold_ore")) {
                    opulent.add(feature + " places " + state);
                }
            }
        }
        assertTrue(opulent.isEmpty(), String.join(", ", opulent));
    }
}
