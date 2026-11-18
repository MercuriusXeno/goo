package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eldritch warrens hold diamond, gold and ancient debris richer than vanilla does: each warren ore feature's count
 * per chunk times its vein size stands over vanilla's for the same ore, and ancient debris's over the nether's two
 * debris features together; and each lunar ore drops what vanilla's ore drops.
 * decision lunar-rock-holds-diamond-gold-and-ancient-debris
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
class LunarWarrenOresTest {

    private static final String WARREN_BIOME = "/data/goo/worldgen/biome/lunar_warrens.json";
    private static final String COUNT = "minecraft:count";

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

    /** A placed feature's veins per chunk times its configured feature's vein size. */
    static int yieldOf(String placedId) throws IOException {
        JsonObject placed = resourceJson(worldgenPath(placedId, "placed_feature"));
        int count = 1;
        for (JsonElement step : placed.getAsJsonArray("placement")) {
            JsonObject modifier = step.getAsJsonObject();
            if (COUNT.equals(modifier.get("type").getAsString())) {
                count *= modifier.get("count").getAsInt();
            }
        }
        JsonObject configured = resourceJson(worldgenPath(placed.get("feature").getAsString(), "configured_feature"));
        return count * configured.getAsJsonObject("config").get("size").getAsInt();
    }

    private static List<String> warrenFeatures() throws IOException {
        List<String> features = new ArrayList<>();
        for (JsonElement step : resourceJson(WARREN_BIOME).getAsJsonArray("features")) {
            step.getAsJsonArray().forEach(feature -> features.add(feature.getAsString()));
        }
        return features;
    }

    @ParameterizedTest
    @CsvSource({"goo:ore_lunar_diamond, minecraft:ore_diamond", "goo:ore_lunar_gold, minecraft:ore_gold"})
    void eachWarrenOreYieldsMoreThanVanillas(String lunar, String vanilla) throws IOException {
        assertTrue(warrenFeatures().contains(lunar), WARREN_BIOME + " names no " + lunar);
        assertTrue(yieldOf(lunar) > yieldOf(vanilla), lunar + " yields " + yieldOf(lunar) + ", " + vanilla + " "
                + yieldOf(vanilla));
    }

    @Test
    void warrenAncientDebrisYieldsMoreThanTheNethersTwoFeatures() throws IOException {
        String lunar = "goo:ore_lunar_ancient_debris";
        assertTrue(warrenFeatures().contains(lunar), WARREN_BIOME + " names no " + lunar);
        int nether = yieldOf("minecraft:ore_debris_small") + yieldOf("minecraft:ore_ancient_debris_large");
        assertTrue(yieldOf(lunar) > nether, lunar + " yields " + yieldOf(lunar) + ", the nether " + nether);
    }

    /** A lunar ore drops itself under silk touch and vanilla's ore drop otherwise. */
    @ParameterizedTest
    @CsvSource({"lunar_diamond_ore, minecraft:diamond", "lunar_gold_ore, minecraft:raw_gold"})
    void lunarOresDropWhatVanillasOresDrop(String block, String drop) throws IOException {
        JsonArray choices = resourceJson("/data/goo/loot_table/blocks/" + block + ".json").getAsJsonArray("pools")
                .get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonArray("children");
        List<String> names = new ArrayList<>();
        choices.forEach(choice -> names.add(choice.getAsJsonObject().get("name").getAsString()));
        assertEquals(List.of("goo:" + block, drop), names);
    }
}
