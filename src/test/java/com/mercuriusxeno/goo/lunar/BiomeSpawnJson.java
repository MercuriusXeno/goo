package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reads a biome file's monster spawners as Minecraft 26.3 writes them: inside the natural mob spawns attribute, each
 * spawner's group size a count that is one number or a uniform range.
 */
final class BiomeSpawnJson {

    private static final String SPAWN_ATTRIBUTE = "minecraft:gameplay/natural_mob_spawns";

    private BiomeSpawnJson() {
    }

    /**
     * @param biome a biome file's JSON
     * @return its monster spawners
     */
    static JsonArray monsterSpawners(JsonObject biome) {
        return biome.getAsJsonObject("attributes").getAsJsonObject(SPAWN_ATTRIBUTE).getAsJsonObject("argument")
                .getAsJsonObject("spawns_by_category").getAsJsonArray("monster");
    }

    /**
     * @param spawner a spawner's JSON
     * @return the largest group it spawns
     */
    static int maxCount(JsonObject spawner) {
        return countBound(spawner.get("count"), "max_inclusive");
    }

    /**
     * @param spawner a spawner's JSON
     * @return the smallest group it spawns
     */
    static int minCount(JsonObject spawner) {
        return countBound(spawner.get("count"), "min_inclusive");
    }

    private static int countBound(JsonElement count, String bound) {
        return count.isJsonPrimitive() ? count.getAsInt() : count.getAsJsonObject().get(bound).getAsInt();
    }
}
