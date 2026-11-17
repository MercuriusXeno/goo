package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunar biome spawns each eldritch mob the overworld spawns, at a weight above its overworld weight, so the
 * eldritch gather on lunar more than anywhere else; and its warrens spawn the monstrosity in larger groups than its
 * surface does.
 * decision eldritch-spawn-in-higher-volumes-on-lunar
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 */
class LunarSpawnsTest {

    private static final String LUNAR_BIOME = "/data/goo/worldgen/biome/lunar.json";
    private static final String WARREN_BIOME = "/data/goo/worldgen/biome/lunar_warrens.json";
    private static final String MONSTROSITY = "goo:eldritch_monstrosity";
    /** The biome modifier adding the eldritch mobs to every overworld biome. */
    private static final String OVERWORLD_ELDRITCH = "/data/goo/neoforge/biome_modifier/eldritch_monstrosity_spawns.json";

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = LunarSpawnsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Map<String, Integer> weights(JsonArray spawners) {
        Map<String, Integer> weights = new HashMap<>();
        for (JsonElement spawner : spawners) {
            JsonObject entry = spawner.getAsJsonObject();
            weights.put(entry.get("type").getAsString(), entry.get("weight").getAsInt());
        }
        return weights;
    }

    private static JsonObject monstrosityIn(String biome) throws IOException {
        for (JsonElement spawner : resourceJson(biome).getAsJsonObject("spawners").getAsJsonArray("monster")) {
            if (MONSTROSITY.equals(spawner.getAsJsonObject().get("type").getAsString())) {
                return spawner.getAsJsonObject();
            }
        }
        throw new AssertionError(biome + " spawns no " + MONSTROSITY);
    }

    /** A monstrosity group in the warrens runs larger than on the surface, at no lesser weight. */
    @Test
    void theWarrensSpawnTheMonstrosityInLargerGroupsThanTheSurface() throws IOException {
        JsonObject surface = monstrosityIn(LUNAR_BIOME);
        JsonObject warrens = monstrosityIn(WARREN_BIOME);
        assertTrue(warrens.get("maxCount").getAsInt() > surface.get("maxCount").getAsInt(),
                "the warrens spawn up to " + warrens.get("maxCount") + ", the surface up to " + surface.get("maxCount"));
        assertTrue(warrens.get("minCount").getAsInt() > surface.get("minCount").getAsInt(),
                "the warrens spawn at least " + warrens.get("minCount") + ", the surface " + surface.get("minCount"));
        assertTrue(warrens.get("weight").getAsInt() >= surface.get("weight").getAsInt(),
                "the warrens weigh " + warrens.get("weight") + ", the surface " + surface.get("weight"));
    }

    @Test
    void lunarSpawnsEachEldritchMobAboveItsOverworldWeight() throws IOException {
        Map<String, Integer> overworld = weights(resourceJson(OVERWORLD_ELDRITCH).getAsJsonArray("spawners"));
        Map<String, Integer> lunar = weights(resourceJson(LUNAR_BIOME).getAsJsonObject("spawners")
                .getAsJsonArray("monster"));
        assertFalse(overworld.isEmpty(), "the overworld spawns no eldritch mob");
        overworld.forEach((mob, weight) -> assertTrue(lunar.getOrDefault(mob, 0) > weight,
                "lunar spawns " + mob + " at " + lunar.getOrDefault(mob, 0) + ", the overworld at " + weight));
    }
}
