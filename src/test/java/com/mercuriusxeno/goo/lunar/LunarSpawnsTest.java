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
 * eldritch gather on lunar more than anywhere else.
 * decision eldritch-spawn-in-higher-volumes-on-lunar
 */
class LunarSpawnsTest {

    private static final String LUNAR_BIOME = "/data/goo/worldgen/biome/lunar.json";
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
