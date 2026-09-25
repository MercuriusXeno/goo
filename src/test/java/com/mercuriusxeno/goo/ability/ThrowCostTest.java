package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.IntUnaryOperator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block_count ability charges each stack for the blocks that stack adds
 * to its footprint, and a cost formula the variants do not hold refuses at
 * load (decision diagnose-then-fix-fuse-and-cost).
 */
class ThrowCostTest {

    private static final String ABILITIES = "data/goo/goo_abilities/";
    private static final int COST_PER_BLOCK = 200;

    private static JsonElement read(String resource) throws IOException {
        try (InputStream in = ThrowCostTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "Classpath holds no " + resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static AbilityDefinition decode(String resource) throws IOException {
        return AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, read(resource))
                .getOrThrow(message -> new IllegalStateException(resource + ": " + message));
    }

    private static IntUnaryOperator footprintOf(String shape) {
        return switch (shape) {
            case "tunnel" -> ChainFootprint::totalBlocks;
            case "flat_circle" -> stacks -> ChainFootprint.flatFootprint(stacks).size();
            default -> throw new IllegalArgumentException(shape);
        };
    }

    @ParameterizedTest
    @CsvSource({
            "rock_tunnel, tunnel, 1", "rock_tunnel, tunnel, 2", "rock_tunnel, tunnel, 3", "rock_tunnel, tunnel, 4",
            "rock_flat, flat_circle, 1", "rock_flat, flat_circle, 2", "rock_flat, flat_circle, 3",
            "rock_flat, flat_circle, 4",
    })
    void throwCost(String ability, String shape, int stack) throws IOException {
        AbilityDefinition definition = decode(ABILITIES + ability + ".json");
        IntUnaryOperator footprint = footprintOf(shape);
        int previous = stack == 1 ? 0 : footprint.applyAsInt(stack - 1);
        int marginalBlocks = footprint.applyAsInt(stack) - previous;

        assertEquals(COST_PER_BLOCK * marginalBlocks, definition.throwCost(stack - 1));
    }

    @Test
    void codec_refusesUnknownFormula() throws IOException {
        JsonElement json = read(ABILITIES + "rock_tunnel.json");
        json.getAsJsonObject().getAsJsonObject("cost").addProperty("formula", "bogus");

        DataResult<AbilityDefinition> parsed = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json);

        assertTrue(parsed.error().isPresent(), "an ability with cost formula \"bogus\" loaded");
        assertTrue(parsed.error().get().message().contains("bogus"), parsed.error().get().message());
    }
}
