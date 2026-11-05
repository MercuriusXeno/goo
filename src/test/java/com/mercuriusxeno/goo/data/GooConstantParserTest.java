package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the game's loader resolves a _constants block by its dependency graph, so a prefab reads a prefab set
 * below it (decision prefabs-resolve-by-dependency-graph-conversions-alone-stay-ordered).
 */
class GooConstantParserTest {

    private static GooValueLoader.ParseState parse(String json) {
        GooValueLoader.ParseState state = new GooValueLoader.ParseState(new HashMap<>(), new HashMap<>(),
                new HashSet<>(), new HashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        GooConstantParser.parseConstants(root, state);
        return state;
    }

    /**
     * $copper_nugget reading the per-type $iron_nugget set below it resolves to twice iron_nugget's metal.
     */
    @Test
    void prefabReadsAPerTypePrefabSetBelowIt() {
        GooValueLoader.ParseState state = parse("""
                {"_constants": {
                    "copper_nugget": "2 $iron_nugget",
                    "iron_nugget": {"metal": "3"}
                }}
                """);
        assertEquals(GooFraction.of(6), state.treeConstants.get("copper_nugget").exact(GooTypes.METAL));
    }

    /**
     * A number prefab reading a number prefab set below it, through another below that, resolves to their product.
     */
    @Test
    void numberPrefabReadsANumberPrefabChainSetBelowIt() {
        GooValueLoader.ParseState state = parse("""
                {"_constants": {
                    "ingot": "$nugget * 9",
                    "nugget": "$base / 2",
                    "base": 4
                }}
                """);
        assertEquals(GooFraction.of(18), state.constants.get("ingot"));
    }

    /**
     * $a reading $b and $b reading $a load no value, and $c beside them loads its number (decision
     * cycled-prefabs-error-and-the-rest-load).
     */
    @Test
    void cycledPrefabsLoadNoValueAndTheRestLoad() {
        GooValueLoader.ParseState state = parse("""
                {"_constants": {"a": "$b + 1", "b": "$a + 1", "c": "4"}}
                """);
        assertEquals(GooFraction.of(4), state.constants.get("c"));
        assertEquals(Set.of("c"), state.constants.keySet());
        assertEquals(Set.of(), state.treeConstants.keySet());
    }
}
