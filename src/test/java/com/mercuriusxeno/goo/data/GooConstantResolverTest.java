package com.mercuriusxeno.goo.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that the workbench's constant resolver answers a _constants block resolved by its dependency graph, each
 * prefab reading any other wherever it sits, and answers in the block's order (decision
 * prefabs-resolve-by-dependency-graph-conversions-alone-stay-ordered).
 */
class GooConstantResolverTest {

    /**
     * $copper_nugget reading $iron_nugget set below it resolves with no error, and the answer keeps the block's order.
     */
    @Test
    void prefabReadingAPrefabBelowItResolvesInTheBlocksOrder() {
        List<GooConstantResolver.ResolvedConstant> resolved = GooConstantResolver.resolve(JsonParser.parseString("""
                {"copper_nugget": "$iron_nugget * 2", "iron_nugget": "$base + 1", "base": 2}
                """).getAsJsonObject());
        assertEquals(List.of("copper_nugget", "iron_nugget", "base"),
                resolved.stream().map(GooConstantResolver.ResolvedConstant::name).toList());
        GooConstantResolver.ResolvedConstant copper = resolved.getFirst();
        assertEquals(GooFraction.of(6), copper.scalar());
        assertNull(copper.error(), copper.error());
    }

    /**
     * A per-type prefab whose amount reads a number prefab set below it resolves with no error.
     */
    @Test
    void perTypePrefabsAmountReadsANumberPrefabBelowIt() {
        GooConstantResolver.ResolvedConstant typed = GooConstantResolver.resolve(JsonParser.parseString("""
                {"typed": {"metal": "$base * 3"}, "base": 2}
                """).getAsJsonObject()).getFirst();
        assertEquals("6", typed.perType().get("metal"));
        assertNull(typed.error(), typed.error());
    }
}
