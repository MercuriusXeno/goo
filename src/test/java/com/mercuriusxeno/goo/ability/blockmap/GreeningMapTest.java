package com.mercuriusxeno.goo.ability.blockmap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped greening map holds the mossy family the operator settled:
 * cobblestone and stone bricks with their stairs, slabs and walls each step
 * to their mossy twin, and dirt steps to grass, one rung each. The file is
 * read off the classpath as plain JSON, so no registry stands.
 * verdant-prism-greens-blocks-slowly
 */
class GreeningMapTest {

    private static final String GREENING = "/data/goo/block_maps/greening.json";
    private static final String STEPS = "steps";
    private static final Map<String, String> MOSSY_FAMILY_AND_GRASS = Map.of(
            "minecraft:cobblestone", "minecraft:mossy_cobblestone",
            "minecraft:cobblestone_stairs", "minecraft:mossy_cobblestone_stairs",
            "minecraft:cobblestone_slab", "minecraft:mossy_cobblestone_slab",
            "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall",
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
            "minecraft:stone_brick_stairs", "minecraft:mossy_stone_brick_stairs",
            "minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab",
            "minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall",
            "minecraft:dirt", "minecraft:grass_block");

    @Test
    void eachMundaneBlockStepsOnceToItsGreenTwin() throws IOException {
        try (InputStream in = GreeningMapTest.class.getResourceAsStream(GREENING)) {
            assertNotNull(in, GREENING);
            JsonObject steps = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject(STEPS);
            assertEquals(MOSSY_FAMILY_AND_GRASS, steps.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAsString())));
        }
    }
}
