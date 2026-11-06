package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An expression reading a goo type its referent holds no amount of loads no value for its entry and names the
 * referent and the type, rather than reading the type as zero: $stem.leaf on a stem holding shroom and nether read 0
 * and leaked nether into sticks and fences (decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line).
 */
class ReferentLacksTypeTest {

    /**
     * A typed prefab reading a type its prefab referent lacks.
     */
    @Nested
    class PrefabReferent {
        private static final String FILE = """
                {
                    "_constants": {
                        "stem": {"shroom": 2, "nether": 2},
                        "stripped_stem": {"shroom": "$stem.leaf / 2"}
                    }
                }
                """;

        /**
         * The validator's warnings hold a line under _constants.stripped_stem naming stem and leaf.
         */
        @Test
        void warningUnderThePrefabNamesTheReferentAndTheType() {
            GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(FILE);
            assertTrue(loaded.warnings().stream().anyMatch(warning -> warning.startsWith("_constants.stripped_stem")
                    && warning.contains("stem holds no leaf")), String.valueOf(loaded.warnings()));
        }
    }

    /**
     * An item entry reading a type its item referent lacks.
     */
    @Nested
    class ItemReferent {
        private static final String FILE = """
                {
                    "crimson_planks": {"nether": 9},
                    "crimson_slab": "crimson_planks.leaf * 2",
                    "crimson_fence": {"nether": "crimson_planks.nether"}
                }
                """;

        /**
         * crimson_slab loads no value, while crimson_fence reading a type crimson_planks holds still loads.
         */
        @Test
        void itemReadingALackedTypeLoadsNoValue() {
            GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(FILE);
            assertFalse(loaded.baseValues().containsKey(Identifier.withDefaultNamespace("crimson_slab")),
                    String.valueOf(loaded.baseValues()));
            assertTrue(loaded.baseValues().containsKey(Identifier.withDefaultNamespace("crimson_fence")),
                    String.valueOf(loaded.baseValues()));
        }

        /**
         * The load records crimson_slab's failure naming crimson_planks and leaf.
         */
        @Test
        void loadNamesTheReferentItemAndTheType() {
            GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(FILE);
            String lacked = loaded.lackedTypes().get(Identifier.withDefaultNamespace("crimson_slab"));
            assertTrue(lacked != null && lacked.contains("crimson_planks holds no leaf"),
                    String.valueOf(loaded.lackedTypes()));
        }
    }
}
