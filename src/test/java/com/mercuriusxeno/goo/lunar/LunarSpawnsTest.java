package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each of lunar's four surface biomes spawns each eldritch mob the overworld spawns, at a weight above its overworld
 * weight, so the eldritch gather on lunar more than anywhere else; and its warrens spawn the monstrosity in larger
 * groups than any surface biome does.
 * decision eldritch-spawn-in-higher-volumes-on-lunar
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 */
class LunarSpawnsTest {

    private static final String BIOME_FOLDER = "/data/goo/worldgen/biome/";
    private static final String WARREN_BIOME = BIOME_FOLDER + "lunar_warrens.json";
    private static final String MONSTROSITY = "goo:eldritch_monstrosity";
    /** The biome modifier adding the eldritch mobs to every overworld biome. */
    private static final String OVERWORLD_ELDRITCH = "/data/goo/neoforge/biome_modifier/eldritch_monstrosity_spawns.json";

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = LunarSpawnsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String surfaceBiome(String name) {
        return BIOME_FOLDER + name + ".json";
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
        for (JsonElement spawner : BiomeSpawnJson.monsterSpawners(resourceJson(biome))) {
            if (MONSTROSITY.equals(spawner.getAsJsonObject().get("type").getAsString())) {
                return spawner.getAsJsonObject();
            }
        }
        throw new AssertionError(biome + " spawns no " + MONSTROSITY);
    }

    /** A monstrosity group in the warrens runs larger than on a surface biome, at no lesser weight. */
    @ParameterizedTest
    @ValueSource(strings = {"lunar_maria", "lunar_highlands", "lunar_shadowed_basins", "lunar_eldritch_ground"})
    void theWarrensSpawnTheMonstrosityInLargerGroupsThanTheSurface(String biome) throws IOException {
        JsonObject surface = monstrosityIn(surfaceBiome(biome));
        JsonObject warrens = monstrosityIn(WARREN_BIOME);
        int warrensMax = BiomeSpawnJson.maxCount(warrens);
        int surfaceMax = BiomeSpawnJson.maxCount(surface);
        int warrensMin = BiomeSpawnJson.minCount(warrens);
        int surfaceMin = BiomeSpawnJson.minCount(surface);
        assertTrue(warrensMax > surfaceMax, "the warrens spawn up to " + warrensMax + ", " + biome + " up to "
                + surfaceMax);
        assertTrue(warrensMin > surfaceMin, "the warrens spawn at least " + warrensMin + ", " + biome + " "
                + surfaceMin);
        assertTrue(warrens.get("weight").getAsInt() >= surface.get("weight").getAsInt(),
                "the warrens weigh " + warrens.get("weight") + ", " + biome + " " + surface.get("weight"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lunar_maria", "lunar_highlands", "lunar_shadowed_basins", "lunar_eldritch_ground"})
    void lunarSpawnsEachEldritchMobAboveItsOverworldWeight(String biome) throws IOException {
        Map<String, Integer> overworld = weights(resourceJson(OVERWORLD_ELDRITCH).getAsJsonArray("spawners"));
        Map<String, Integer> lunar = weights(BiomeSpawnJson.monsterSpawners(resourceJson(surfaceBiome(biome))));
        assertFalse(overworld.isEmpty(), "the overworld spawns no eldritch mob");
        overworld.forEach((mob, weight) -> assertTrue(lunar.getOrDefault(mob, 0) > weight,
                biome + " spawns " + mob + " at " + lunar.getOrDefault(mob, 0) + ", the overworld at " + weight));
    }
}
