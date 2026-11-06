package com.mercuriusxeno.goo.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Each prefab in the $a/$b cycle answers no value and an error naming both, and $c beside them resolves with none
     * (decision cycled-prefabs-error-and-the-rest-load).
     */
    @Test
    void eachCycledPrefabErrorsNamingItsCycleAndTheRestResolve() {
        List<GooConstantResolver.ResolvedConstant> resolved = GooConstantResolver.resolve(JsonParser.parseString("""
                {"a": "$b + 1", "b": "$a + 1", "c": "4"}
                """).getAsJsonObject());
        for (GooConstantResolver.ResolvedConstant cycled : resolved.subList(0, 2)) {
            assertEquals("$a, $b read each other in a cycle and load no value", cycled.error(), cycled.name());
            assertNull(cycled.scalar(), cycled.name());
        }
        assertEquals(GooFraction.of(4), resolved.get(2).scalar());
        assertNull(resolved.get(2).error(), resolved.get(2).error());
    }

    /**
     * Resolving a type its referent lacks (decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line).
     */
    @Nested
    class ReferentLackingTheType {

        /**
         * stripped_stem reading $stem.leaf, stem holding shroom and nether, answers no per-type value and an error
         * naming stem and leaf.
         */
        @Test
        void typedPrefabReadingATypeItsPrefabLacksErrorsNamingBoth() {
            GooConstantResolver.ResolvedConstant stripped = GooConstantResolver.resolve(JsonParser.parseString("""
                    {"stem": {"shroom": 2, "nether": 2}, "stripped_stem": {"shroom": "$stem.leaf / 2"}}
                    """).getAsJsonObject()).get(1);
            assertTrue(stripped.perType().isEmpty(), String.valueOf(stripped.perType()));
            assertTrue(stripped.error() != null && stripped.error().contains("stem holds no leaf"), stripped.error());
        }

        /**
         * A referent in string form, hyphae reading "4 $stem / 3", holds the types its expression resolves to, so
         * $hyphae.leaf errors from the evaluator where the validator cannot see hyphae's types.
         */
        @Test
        void typedPrefabReadingATypeItsStringFormReferentLacksErrors() {
            GooConstantResolver.ResolvedConstant stripped = GooConstantResolver.resolve(JsonParser.parseString("""
                    {"stem": {"shroom": 3, "nether": 3}, "hyphae": "4 $stem / 3",
                     "stripped_hyphae": {"shroom": "$hyphae.leaf"}}
                    """).getAsJsonObject()).get(2);
            assertTrue(stripped.perType().isEmpty(), String.valueOf(stripped.perType()));
            assertTrue(stripped.error() != null && stripped.error().contains("hyphae holds no leaf"), stripped.error());
        }

        /**
         * A typed prefab reading a type its referent holds resolves with no error.
         */
        @Test
        void typedPrefabReadingAHeldTypeResolves() {
            GooConstantResolver.ResolvedConstant stripped = GooConstantResolver.resolve(JsonParser.parseString("""
                    {"stem": {"shroom": 2, "nether": 2}, "stripped_stem": {"shroom": "$stem.shroom / 2"}}
                    """).getAsJsonObject()).get(1);
            assertEquals("1", stripped.perType().get("shroom"));
            assertNull(stripped.error(), stripped.error());
        }
    }
}
