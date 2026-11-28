package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the value registry, seeded from the shipped base values and deriving iron, the bucket obsidian reads its
 * value through, and the anvil from their vanilla recipes, gives durable goo to things that have toughness, keeping
 * every type each held before.
 * decision durable-is-found-in-things-that-have-toughness
 */
class DurableSourcesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final Map<String, List<ResourceKey<GooTypeDefinition>>> TOUGH_ITEMS_AND_THEIR_FORMER_TYPES = Map.of(
            "diamond", List.of(GooTypes.CRYSTAL, GooTypes.ORNATE),
            "netherite_ingot", List.of(GooTypes.METAL, GooTypes.NETHER, GooTypes.ORNATE),
            "anvil", List.of(GooTypes.METAL),
            "obsidian", List.of(GooTypes.ROCK, GooTypes.CRYSTAL),
            "deepslate", List.of(GooTypes.ROCK),
            "leather", List.of(GooTypes.YARN),
            "iron_ingot", List.of(GooTypes.METAL));
    private static final ResourceKey<GooTypeDefinition> DURABLE = GooTypes.bundled("durable");
    private static GooValueRegistry registry;

    @BeforeAll
    static void deriveTheShippedValues() throws IOException {
        registry = new GooValueRegistry();
        try (InputStream in = DurableSourcesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES + " is not on the classpath");
            registry.parseBaseValues(in);
        }
        registry.publishBaseAsEffective();
        registry.deriveFromRecipeInputs(List.of(
                recipe("minecraft:iron_ingot", 1, nine("minecraft:iron_nugget")),
                recipe("minecraft:iron_block", 1, nine("minecraft:iron_ingot")),
                recipe("minecraft:bucket", 1, slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                        slot("minecraft:iron_ingot")),
                recipe("minecraft:anvil", 1, slot("minecraft:iron_block"), slot("minecraft:iron_block"),
                        slot("minecraft:iron_block"), slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                        slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"))), false);
    }

    @ParameterizedTest
    @MethodSource("toughItems")
    void toughItemYieldsDurableAndKeepsItsTypes(String item) {
        GooValue value = valueOf(item);
        assertTrue(value.exact(DURABLE).isPositive(), item + " yields no durable: " + value);
        for (ResourceKey<GooTypeDefinition> type : TOUGH_ITEMS_AND_THEIR_FORMER_TYPES.get(item)) {
            assertTrue(value.exact(type).isPositive(), item + " lost its " + GooTypes.id(type) + ": " + value);
        }
    }

    @Test
    void ironIngotYieldsLessDurableThanADiamond() {
        GooFraction iron = valueOf("iron_ingot").exact(DURABLE);
        GooFraction diamond = valueOf("diamond").exact(DURABLE);
        assertTrue(iron.compareTo(diamond) < 0, "iron ingot " + iron + " is not below diamond " + diamond);
    }

    @Test
    void dirtYieldsNoDurable() {
        assertTrue(valueOf("dirt").exact(DURABLE).isZero(), "dirt yields durable");
    }

    static Stream<String> toughItems() {
        return TOUGH_ITEMS_AND_THEIR_FORMER_TYPES.keySet().stream().sorted();
    }

    private static GooValue valueOf(String item) {
        GooValue value = registry.table().lookup(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "the registry values no " + item);
        return value;
    }

    private static String[][] nine(String item) {
        String[][] slots = new String[9][];
        Arrays.fill(slots, slot(item));
        return slots;
    }
}
