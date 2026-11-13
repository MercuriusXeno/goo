package com.mercuriusxeno.goo.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The six machine crafting recipes, read from their shipped JSON on the classpath: the key holding the cells
 * copper held names gold, one to one, a gold block where a copper block stood.
 */
class MachineRecipesTest {

    private static final String RECIPES = "/data/goo/recipe/";

    // decision copper-to-gold-one-to-one-scrap-stays
    @ParameterizedTest
    @CsvSource({"reactor, S, minecraft:gold_ingot", "plexer, S, minecraft:gold_ingot",
            "crucible, I, minecraft:gold_ingot", "hub, C, minecraft:gold_ingot",
            "vat, B, minecraft:gold_block", "canister, C, minecraft:gold_ingot"})
    void eachMachinesMetalKeyNamesGold(String machine, String symbol, String gold) throws IOException {
        assertEquals(gold, recipe(machine).getAsJsonObject("key").get(symbol).getAsString());
    }

    private static JsonObject recipe(String machine) throws IOException {
        String path = RECIPES + machine + ".json";
        try (InputStream in = MachineRecipesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
