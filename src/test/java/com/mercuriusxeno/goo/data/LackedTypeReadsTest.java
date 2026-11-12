package com.mercuriusxeno.goo.data;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An expression reading a goo type its referent holds no amount of reads that type as zero, so its entry or its
 * constant still loads, and the load records the referent and the type for the workbench's red line
 * (decisions absent-goo-type-reads-zero-in-the-game, type-the-referent-lacks-loads-no-value-and-reads-a-red-line).
 */
class LackedTypeReadsTest {

    /**
     * An item entry reading a type its prefab referent lacks.
     */
    @Nested
    class ItemEntry {
        private static final String FILE = """
                {
                    "_constants": {"dye": {"opulent": 6}},
                    "a": {"zoo": "3 $dye.leaf / 2 + 4"}
                }
                """;

        /**
         * a loads zoo 4, $dye.leaf reading zero.
         */
        @Test
        void entryReadingALackedTypeLoadsWithTheTypeReadAsZero() {
            GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(FILE);
            GooValue a = loaded.baseValues().get(Identifier.withDefaultNamespace("a"));
            assertNotNull(a, String.valueOf(loaded.baseValues()));
            assertEquals(GooFraction.of(4), a.exact(GooTypes.ZOO), String.valueOf(a));
        }

        /**
         * The load records a's read naming dye and leaf, which the workbench draws beside the entry.
         */
        @Test
        void loadNamesTheReferentAndTheType() {
            GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(FILE);
            assertEquals("dye holds no leaf", loaded.lackedTypes().get(Identifier.withDefaultNamespace("a")),
                    String.valueOf(loaded.lackedTypes()));
        }
    }

    /**
     * An item entry reading a type its item referent lacks, as an item-level expression.
     */
    @Test
    void itemExpressionReadingALackedTypeLoadsTheRest() {
        GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load("""
                {
                    "crimson_planks": {"nether": 9},
                    "crimson_slab": "crimson_planks.leaf * 2 + crimson_planks"
                }
                """);
        GooValue slab = loaded.baseValues().get(Identifier.withDefaultNamespace("crimson_slab"));
        assertNotNull(slab, String.valueOf(loaded.baseValues()));
        assertEquals(GooFraction.of(9), slab.exact(GooTypes.NETHER), String.valueOf(slab));
        assertEquals("crimson_planks holds no leaf",
                loaded.lackedTypes().get(Identifier.withDefaultNamespace("crimson_slab")));
    }

    /**
     * A constant reading a type its prefab referent lacks.
     */
    @Nested
    class Constant {
        private static GooValueLoader.ParseState parsed(String constants) {
            GooValueLoader.ParseState state = new GooValueLoader.ParseState(new HashMap<>(), new HashMap<>(),
                    new HashSet<>(), new HashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
            GooConstantParser.parseConstants(JsonParser.parseString("{\"_constants\": " + constants + "}")
                    .getAsJsonObject(), state);
            return state;
        }

        /**
         * stripped_stem's shroom reading $stem.leaf + 3, stem holding shroom alone, resolves to shroom 3.
         */
        @Test
        void typedPrefabReadingALackedTypeResolvesWithTheTypeReadAsZero() {
            GooValueLoader.ParseState state = parsed("""
                    {"stem": {"shroom": 2}, "stripped_stem": {"shroom": "$stem.leaf + 3"}}
                    """);
            GooValue stripped = state.treeConstants.get("stripped_stem");
            assertNotNull(stripped, String.valueOf(state.treeConstants));
            assertEquals(GooFraction.of(3), stripped.exact(GooTypes.SHROOM), String.valueOf(stripped));
        }

        /**
         * half_stem reading $stem.leaf / 2 stands as a constant rather than loading none.
         */
        @Test
        void stringPrefabReadingALackedTypeStands() {
            GooValueLoader.ParseState state = parsed("""
                    {"stem": {"shroom": 2}, "half_stem": "$stem.leaf / 2"}
                    """);
            assertTrue(state.treeConstants.containsKey("half_stem") || state.constants.containsKey("half_stem"),
                    state.treeConstants + " " + state.constants);
        }
    }
}
